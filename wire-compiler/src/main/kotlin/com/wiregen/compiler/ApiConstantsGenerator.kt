package com.wiregen.compiler

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.wiregen.annotation.WIRE_UNSET
import com.wiregen.annotation.WireNameEncoder
import com.wiregen.annotation.WireNameRule
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.io.FileAlreadyExistsException

internal class ApiConstantsGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: PathOptions,
    private val dict: Map<String, String>,
    private val written: MutableSet<String>,
) {
    fun generate(objects: List<KSClassDeclaration>): List<PathObjectMapping> {
        val identities = uniqueIdentities(objects.mapNotNull(::identity))
        val mappings = mutableListOf<PathObjectMapping>()
        identities.forEach { identity ->
            val spec = parse(identity) ?: return@forEach
            write(spec)
            mappings += pathMapping(spec)
        }
        return mappings
    }

    private fun identity(declaration: KSClassDeclaration): PathIdentity? {
        val annotation = declaration.annotation(GEN_API) ?: return null
        if (declaration.classKind != ClassKind.OBJECT) {
            logger.error("@GenApiConstants is only allowed on object", declaration)
            return null
        }
        val simpleName = declaration.simpleName.asString()
        val sourcePackage = declaration.packageName.asString()
        val sourceQualified = declaration.qualifiedName?.asString()
        val originating = declaration.containingFile
        if (sourceQualified == null || originating == null) {
            logger.error("@GenApiConstants type must be a source object", declaration)
            return null
        }

        val classPrefix = pick(annotation.string("classPrefix"), options.classPrefix)
        val classSuffix = pick(annotation.string("classSuffix"), options.classSuffix)
        val packageName = pick(annotation.string("packageName"), options.packageName).ifBlank { sourcePackage }
        val className = NameTransformer.className(simpleName, classPrefix, classSuffix)
        var valid = true
        if (!NameTransformer.isIdentifier(className)) {
            logger.error("generated object name '$className' is not an identifier", declaration)
            valid = false
        }
        if (packageName == sourcePackage && className == simpleName) {
            logger.error("generated object '$className' clashes with its spec; set a prefix or suffix", declaration)
            valid = false
        }
        if (packageName.isNotEmpty() && !NameTransformer.isQualifiedName(packageName)) {
            logger.error("packageName '$packageName' is not a package name", declaration)
            valid = false
        }
        val rule = annotation.rule()
        val pathRule = when (rule) {
            null -> {
                valid = false
                options.nameRule
            }
            WireNameRule.OPTION -> options.nameRule
            else -> rule.name.lowercase()
        }
        val encoder = if (!valid) {
            null
        } else {
            try {
                resolveEncoder(declaration, annotation, rule)
            } catch (error: IllegalArgumentException) {
                logger.error(error.message ?: "cannot load pathEncoder", declaration)
                null
            }
        }
        if (!valid || (encoder == null && wantsEncoder(annotation, rule))) return null
        val sourceText = try {
            File(originating.filePath).readText()
        } catch (error: Exception) {
            logger.error("cannot read ${originating.filePath}: ${error.message}", declaration)
            return null
        }
        return PathIdentity(
            declaration = declaration,
            originating = originating,
            sourceText = sourceText,
            sourceQualified = sourceQualified,
            packageName = packageName,
            className = className,
            pathRule = pathRule,
            encoder = encoder,
        )
    }

    private fun wantsEncoder(annotation: KSAnnotation, rule: WireNameRule?): Boolean {
        if (annotation.encoderClassName() != null) return true
        return (rule == null || rule == WireNameRule.OPTION) && options.nameEncoder.isNotBlank()
    }

    private fun resolveEncoder(
        declaration: KSClassDeclaration,
        annotation: KSAnnotation,
        rule: WireNameRule?,
    ): WireNameEncoder? {
        val specEncoder = annotation.encoderClassName()
        if (specEncoder != null) {
            if (rule != null && rule != WireNameRule.OPTION) {
                logger.warn("pathEncoder replaces pathRule on ${declaration.simpleName.asString()}", declaration)
            }
            return WireNameEncoders.load(specEncoder)
        }
        if (rule != null && rule != WireNameRule.OPTION) return null
        if (options.nameEncoder.isBlank()) return null
        return WireNameEncoders.load(options.nameEncoder)
    }

    private fun parse(identity: PathIdentity): PathSpec? {
        val constants = mutableListOf<PathConstantSpec>()
        var valid = true
        identity.declaration.getDeclaredProperties().forEach { property ->
            if (Modifier.PRIVATE in property.modifiers) return@forEach
            val constant = parseConstant(identity, property)
            if (constant == null) valid = false else constants += constant
        }
        if (!valid) return null
        constants.groupBy { it.name }
            .filter { it.value.size > 1 }
            .forEach { (name, items) ->
                logger.error("duplicate const '$name'", items.first().property)
                valid = false
            }
        if (!valid) return null
        return PathSpec(identity, constants)
    }

    private fun parseConstant(identity: PathIdentity, property: KSPropertyDeclaration): PathConstantSpec? {
        if (Modifier.CONST !in property.modifiers) {
            logger.error(
                "@GenApiConstants only supports const val String members; '${property.simpleName.asString()}' is not const",
                property,
            )
            return null
        }
        val type = property.type.resolve()
        val typeName = type.declaration.qualifiedName?.asString()
        if (typeName != "kotlin.String" || type.isMarkedNullable) {
            logger.error(
                "const '${property.simpleName.asString()}' must be a non-null String",
                property,
            )
            return null
        }
        val override = apiPath(property)
        val name = property.simpleName.asString()
        val literalExpr = KotlinSource(identity.sourceText).memberDefault(
            identity.declaration.simpleName.asString(),
            name,
        )
        if (literalExpr == null) {
            logger.error("could not read initializer of $name", property)
            return null
        }
        val literal = NameTransformer.parseKotlinStringLiteral(literalExpr)
        if (literal == null) {
            logger.error(
                "const '$name' must be initialized with a string literal, got $literalExpr",
                property,
            )
            return null
        }
        val semantic = override.key.ifBlank { literal }
        if (semantic.any { it == '\n' || it == '\r' }) {
            logger.error("semantic path for '$name' spans multiple lines", property)
            return null
        }
        val wire = try {
            when {
                override.raw.isNotEmpty() -> override.raw
                identity.encoder != null -> NameTransformer.encodePath(
                    semantic,
                    options.encodeMode,
                ) { segment -> identity.encoder.encode(segment) }
                else -> NameTransformer.encodePath(semantic, options.encodeMode) { segment ->
                    NameTransformer.wireName(segment, identity.pathRule, dict, options.xorKey)
                }
            }
        } catch (error: Exception) {
            logger.error(error.message ?: "failed to encode path for $name", property)
            return null
        }
        if (wire.isEmpty()) {
            logger.error("encoded path for '$name' is empty", property)
            return null
        }
        return PathConstantSpec(
            property = property,
            name = name,
            semantic = semantic,
            wire = wire,
        )
    }

    private fun pathMapping(spec: PathSpec): PathObjectMapping {
        val identity = spec.identity
        val generated = if (identity.packageName.isEmpty()) {
            identity.className
        } else {
            "${identity.packageName}.${identity.className}"
        }
        return PathObjectMapping(
            source = identity.sourceQualified,
            generated = generated,
            paths = spec.constants.map { constant ->
                PathConstantMapping(
                    name = constant.name,
                    semantic = constant.semantic,
                    wire = constant.wire,
                )
            },
        )
    }

    private fun write(spec: PathSpec) {
        val identity = spec.identity
        val key = "${identity.packageName}.${identity.className}"
        if (!written.add(key)) return
        val source = renderSource(spec)
        try {
            codeGenerator.createNewFile(
                Dependencies(aggregating = false, identity.originating),
                identity.packageName,
                identity.className,
            ).use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(source)
                }
            }
        } catch (error: FileAlreadyExistsException) {
            error.file.writeText(source)
        }
    }

    private fun renderSource(spec: PathSpec): String {
        val identity = spec.identity
        return buildString {
            appendLine("// Generated by wire-compiler from ${identity.sourceQualified}. Do not edit.")
            appendLine()
            if (identity.packageName.isNotEmpty()) {
                append("package ").append(identity.packageName).appendLine()
                appendLine()
            }
            append("object ").append(identity.className).appendLine(" {")
            if (spec.constants.isEmpty()) {
                appendLine()
            } else {
                spec.constants.forEach { constant ->
                    append("    const val ").append(constant.name).append(": String = ")
                    append(NameTransformer.kotlinStringLiteral(constant.wire))
                    append(" // ").append(constant.semantic)
                    appendLine()
                }
            }
            appendLine("}")
        }
    }

    private fun uniqueIdentities(identities: List<PathIdentity>): List<PathIdentity> {
        val unique = mutableListOf<PathIdentity>()
        identities.groupBy { "${it.packageName}.${it.className}" }.forEach { (name, items) ->
            if (items.size == 1) {
                unique += items.first()
            } else {
                items.forEach { identity ->
                    logger.error("duplicate generated object $name", identity.declaration)
                }
            }
        }
        return unique
    }

    private fun apiPath(property: KSPropertyDeclaration): PathOverride {
        val annotation = property.annotations.firstOrNull { item ->
            item.annotationType.resolve().declaration.qualifiedName?.asString() == API_PATH
        } ?: return PathOverride()
        return PathOverride(
            key = annotation.string("key").orEmpty(),
            raw = annotation.string("raw").orEmpty(),
        )
    }

    private fun KSClassDeclaration.annotation(qualifiedName: String): KSAnnotation? =
        annotations.firstOrNull { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        }

    private fun KSAnnotation.string(name: String): String? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String

    private fun KSAnnotation.encoderClassName(): String? {
        val raw = arguments.firstOrNull { it.name?.asString() == "pathEncoder" }?.value ?: return null
        val qualified = when (raw) {
            is KSType -> raw.declaration.qualifiedName?.asString()
            else -> raw.toString().substringBefore('@').takeIf { it.contains('.') }
        } ?: return null
        if (qualified == ENCODER_INTERFACE) return null
        return qualified
    }

    private fun KSAnnotation.rule(): WireNameRule? {
        val raw = arguments.firstOrNull { it.name?.asString() == "pathRule" }?.value
            ?: return WireNameRule.OPTION
        if (raw is WireNameRule) return raw
        val entry = when (raw) {
            is KSType -> raw.declaration.simpleName.asString()
            else -> raw.toString().substringAfterLast('.').substringBefore('@')
        }
        return try {
            WireNameRule.valueOf(entry)
        } catch (_: IllegalArgumentException) {
            logger.error("unknown WireNameRule '$entry'")
            null
        }
    }

    private fun pick(explicit: String?, fallback: String): String =
        if (explicit == null || explicit == WIRE_UNSET) fallback else explicit

    private companion object {
        const val GEN_API = "com.wiregen.annotation.GenApiConstants"
        const val API_PATH = "com.wiregen.annotation.ApiPath"
        const val ENCODER_INTERFACE = "com.wiregen.annotation.WireNameEncoder"
    }
}

private data class PathOverride(
    val key: String = "",
    val raw: String = "",
)

private data class PathIdentity(
    val declaration: KSClassDeclaration,
    val originating: KSFile,
    val sourceText: String,
    val sourceQualified: String,
    val packageName: String,
    val className: String,
    val pathRule: String,
    val encoder: WireNameEncoder?,
)

private data class PathConstantSpec(
    val property: KSPropertyDeclaration,
    val name: String,
    val semantic: String,
    val wire: String,
)

private data class PathSpec(
    val identity: PathIdentity,
    val constants: List<PathConstantSpec>,
)
