package com.wiregen.compiler

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
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
import com.wiregen.annotation.WireMutability
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.io.FileAlreadyExistsException

internal class WireModelGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val resolver: Resolver,
    private val options: WireModelOptions,
    private val dict: Map<String, String>,
    private val written: MutableSet<String>,
) {
    fun generate(classes: List<KSClassDeclaration>): List<WireClassMapping> {
        val annotated = classes.mapNotNull { it.qualifiedName?.asString() }.toSet()
        val identities = uniqueIdentities(classes.mapNotNull(::identity))
        val generatedBySource = identities.associate { identity ->
            identity.sourceQualified to "${identity.packageName}.${identity.className}"
        }
        val mappings = mutableListOf<WireClassMapping>()
        identities.forEach { identity ->
            val spec = parse(identity) ?: return@forEach
            val renderer = TypeRenderer(generatedBySource, annotated, identity.packageName)
            val source = renderSource(spec, renderer) ?: return@forEach
            write(spec, source)
            mappings += wireMapping(spec)
        }
        return mappings
    }

    private fun wireMapping(spec: ModelSpec): WireClassMapping {
        val identity = spec.identity
        val generated = if (identity.packageName.isEmpty()) {
            identity.className
        } else {
            "${identity.packageName}.${identity.className}"
        }
        return WireClassMapping(
            source = identity.sourceQualified,
            generated = generated,
            fields = spec.fields.map { field ->
                WireFieldMapping(
                    semantic = field.semantic,
                    param = field.paramName,
                    wire = field.wireName,
                )
            },
        )
    }

    private fun identity(declaration: KSClassDeclaration): ModelIdentity? {
        val annotation = declaration.annotation(GEN_MODEL) ?: return null
        val simpleName = declaration.simpleName.asString()
        val sourcePackage = declaration.packageName.asString()
        val sourceQualified = declaration.qualifiedName?.asString()
        val originating = declaration.containingFile
        if (sourceQualified == null || originating == null) {
            logger.error("@GenModel type must be a source class", declaration)
            return null
        }
        if (declaration.typeParameters.isNotEmpty()) {
            logger.error("@GenModel does not support generic specs", declaration)
            return null
        }

        val classPrefix = pick(annotation.string("classPrefix"), options.classPrefix)
        val classSuffix = pick(annotation.string("classSuffix"), options.classSuffix)
        val paramPrefix = pick(annotation.string("paramPrefix"), options.paramPrefix)
        val paramSuffix = pick(annotation.string("paramSuffix"), options.paramSuffix)
        val annotatedSuper = pick(annotation.string("superClass"), options.superClass)
        val superArgs = pick(annotation.string("superArgs"), options.superArgs)
        val packageName = pick(annotation.string("packageName"), options.packageName).ifBlank { sourcePackage }
        val className = NameTransformer.className(simpleName, classPrefix, classSuffix)
        var valid = true
        if (!NameTransformer.isIdentifier(className)) {
            logger.error("generated class name '$className' is not an identifier", declaration)
            valid = false
        }
        if (packageName == sourcePackage && className == simpleName) {
            logger.error("generated class '$className' clashes with its spec; set a prefix or suffix", declaration)
            valid = false
        }
        if (annotatedSuper.isNotEmpty() && !NameTransformer.isQualifiedName(annotatedSuper)) {
            logger.error("superClass '$annotatedSuper' is not a qualified class name", declaration)
            valid = false
        }
        if (packageName.isNotEmpty() && !NameTransformer.isQualifiedName(packageName)) {
            logger.error("packageName '$packageName' is not a package name", declaration)
            valid = false
        }
        val rule = annotation.rule()
        val nameRule = when (rule) {
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
                logger.error(error.message ?: "cannot load nameEncoder", declaration)
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
        val kotlinSource = KotlinSource(sourceText)
        val declaredParents = declaration.superTypes.mapNotNull { reference ->
            reference.resolve().declaration.qualifiedName?.asString()
        }.filter { it != "kotlin.Any" && it != "java.lang.Object" }.distinct().toList()
        val parentNames: List<String>
        val superClause: String
        if (declaredParents.isNotEmpty()) {
            parentNames = declaredParents
            superClause = kotlinSource.supertypeClause(simpleName) ?: synthesizeClause(declaredParents)
        } else if (annotatedSuper.isNotEmpty()) {
            if (resolver.getClassDeclarationByName(annotatedSuper) == null) {
                logger.warn("superClass $annotatedSuper was not found on the classpath", declaration)
            }
            parentNames = listOf(annotatedSuper)
            superClause = "${annotatedSuper.substringAfterLast('.')}($superArgs)"
        } else {
            parentNames = emptyList()
            superClause = ""
        }
        return ModelIdentity(
            declaration = declaration,
            originating = originating,
            sourceText = sourceText,
            sourceQualified = sourceQualified,
            packageName = packageName,
            className = className,
            paramPrefix = paramPrefix,
            paramSuffix = paramSuffix,
            parentQualifiedNames = parentNames,
            superClause = superClause,
            nameRule = nameRule,
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
                logger.warn("nameEncoder replaces nameRule on ${declaration.simpleName.asString()}", declaration)
            }
            return WireNameEncoders.load(specEncoder)
        }
        if (rule != null && rule != WireNameRule.OPTION) return null
        if (options.nameEncoder.isBlank()) return null
        return WireNameEncoders.load(options.nameEncoder)
    }

    private fun synthesizeClause(parents: List<String>): String =
        parents.joinToString(", ") { qualified ->
            val simple = qualified.substringAfterLast('.')
            val kind = resolver.getClassDeclarationByName(qualified)?.classKind
            if (kind == ClassKind.INTERFACE) simple else "$simple()"
        }

    private fun parse(identity: ModelIdentity): ModelSpec? {
        val fields = mutableListOf<FieldSpec>()
        var valid = true
        declaredMembers(identity.declaration).forEach { property ->
            val field = parseField(identity, property)
            if (field == null) valid = false else fields += field
        }
        if (!valid) return null
        reportDuplicates(identity.declaration, fields)
        if (fields.groupBy { it.paramName }.any { it.value.size > 1 }) return null
        if (fields.groupBy { it.wireName }.any { it.value.size > 1 }) return null
        return ModelSpec(identity, fields)
    }

    private fun declaredMembers(declaration: KSClassDeclaration): List<KSPropertyDeclaration> {
        val properties = declaration.getDeclaredProperties().filter { property ->
            Modifier.PRIVATE !in property.modifiers && Modifier.OVERRIDE !in property.modifiers
        }.toList()
        val constructorOrder = declaration.primaryConstructor?.parameters
            ?.mapNotNull { it.name?.asString() }
            .orEmpty()
        val byName = properties.associateBy { it.simpleName.asString() }
        val inConstructor = constructorOrder.mapNotNull { byName[it] }
        val rest = properties.filter { it.simpleName.asString() !in constructorOrder.toSet() }
        return inConstructor + rest
    }

    private fun parseField(identity: ModelIdentity, property: KSPropertyDeclaration): FieldSpec? {
        val override = wireField(identity.declaration, property) ?: return null
        val propertyName = property.simpleName.asString()
        val semantic = override.key.ifBlank { propertyName }
        val paramName = NameTransformer.paramName(propertyName, identity.paramPrefix, identity.paramSuffix)
        if (!NameTransformer.isIdentifier(paramName)) {
            logger.error("generated parameter name '$paramName' is not an identifier", property)
            return null
        }
        if (semantic.isBlank() || semantic.any { it == '\n' || it == '\r' }) {
            logger.error("semantic name '$semantic' is blank or spans multiple lines", property)
            return null
        }
        val wireName = try {
            when {
                override.raw.isNotEmpty() -> override.raw
                identity.encoder != null -> identity.encoder.encode(semantic)
                else -> NameTransformer.wireName(semantic, identity.nameRule, dict, options.xorKey)
            }
        } catch (error: Exception) {
            logger.error(error.message ?: "failed to build SerializedName", property)
            return null
        }
        if (wireName.isEmpty()) {
            logger.error("SerializedName for '$propertyName' is empty", property)
            return null
        }
        val hiddenParent = identity.parentQualifiedNames.firstOrNull { parent ->
            hidesSuperMember(parent, paramName)
        }
        if (hiddenParent != null) {
            logger.error("parameter '$paramName' hides a member of $hiddenParent", property)
            return null
        }
        val type = property.type.resolve()
        val constructorParam = identity.declaration.primaryConstructor?.parameters?.firstOrNull { parameter ->
            parameter.name?.asString() == propertyName
        }
        val sourceDefault = KotlinSource(identity.sourceText).memberDefault(
            identity.declaration.simpleName.asString(),
            propertyName,
        )
        val defaultCode = when {
            override.defaultCode.isNotEmpty() -> override.defaultCode
            sourceDefault != null -> sourceDefault
            constructorParam?.hasDefault == true -> {
                logger.error("could not read the default value of $propertyName", property)
                return null
            }
            type.isMarkedNullable -> "null"
            else -> null
        }
        val mutable = when (override.mutable) {
            WireMutability.FOLLOW -> property.isMutable
            WireMutability.VAL -> false
            WireMutability.VAR -> true
        }
        return FieldSpec(
            property = property,
            paramName = paramName,
            semantic = semantic,
            wireName = wireName,
            type = type,
            defaultCode = defaultCode,
            mutable = mutable,
        )
    }

    private fun renderSource(spec: ModelSpec, renderer: TypeRenderer): String? {
        val renderedFields = mutableListOf<String>()
        spec.fields.forEach { field ->
            val typeName = try {
                renderer.render(field.type)
            } catch (error: IllegalArgumentException) {
                logger.error(
                    error.message ?: "failed to render type of ${field.paramName}",
                    field.property,
                )
                return null
            }
            renderedFields += renderField(field, typeName)
        }
        val identity = spec.identity
        val kotlinSource = KotlinSource(identity.sourceText)
        val specImports = kotlinSource.explicitImports()
        val referenced = linkedSetOf<String>()
        identity.parentQualifiedNames.forEach { referenced += it }
        spec.fields.forEach { field ->
            val code = field.defaultCode ?: return@forEach
            kotlinSource.simpleIdentifiers(code).forEach { name ->
                specImports[name]?.let { referenced += it }
            }
        }
        identity.superClause.takeIf { it.isNotEmpty() }?.let { clause ->
            kotlinSource.simpleIdentifiers(clause).forEach { name ->
                specImports[name]?.let { referenced += it }
            }
        }
        val imports = buildSet {
            if (renderedFields.isNotEmpty()) add("com.google.gson.annotations.SerializedName")
            renderer.imports.filterTo(this) { TypeRenderer.shouldImport(it, identity.packageName) }
            referenced.filterTo(this) { TypeRenderer.shouldImport(it, identity.packageName) }
        }.sorted()
        val header = buildString {
            appendLine("// Generated by wire-compiler from ${identity.sourceQualified}. Do not edit.")
            appendLine()
            if (identity.packageName.isNotEmpty()) {
                append("package ").append(identity.packageName).appendLine()
                appendLine()
            }
            if (imports.isNotEmpty()) {
                imports.forEach { append("import ").append(it).appendLine() }
                appendLine()
            }
            append("data class ").append(identity.className).append("(")
        }
        val body = if (renderedFields.isEmpty()) {
            ")"
        } else {
            renderedFields.joinToString(prefix = "\n", postfix = "\n)", separator = "\n")
        }
        val parent = if (identity.superClause.isEmpty()) "" else " : ${identity.superClause}"
        return header + body + parent + "\n"
    }

    private fun renderField(field: FieldSpec, typeName: String): String {
        val keyword = if (field.mutable) "var" else "val"
        val defaultValue = field.defaultCode?.let { code -> " = ${formatDefault(code)}" }.orEmpty()
        return buildString {
            append("    @SerializedName(")
            append(NameTransformer.kotlinStringLiteral(field.wireName))
            appendLine(")")
            append("    ").append(keyword).append(" ").append(field.paramName)
            append(": ").append(typeName).append(defaultValue)
            append(",")
            append(" // ").append(field.semantic)
        }
    }

    private fun write(spec: ModelSpec, source: String) {
        val identity = spec.identity
        val key = "${identity.packageName}.${identity.className}"
        if (!written.add(key)) return
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
            // This round already created the file, or a previous round left it in place.
            error.file.writeText(source)
        }
    }

    private fun uniqueIdentities(identities: List<ModelIdentity>): List<ModelIdentity> {
        val unique = mutableListOf<ModelIdentity>()
        identities.groupBy { "${it.packageName}.${it.className}" }.forEach { (name, items) ->
            if (items.size == 1) {
                unique += items.first()
            } else {
                items.forEach { identity ->
                    logger.error("duplicate generated class $name", identity.declaration)
                }
            }
        }
        return unique
    }

    private fun reportDuplicates(declaration: KSClassDeclaration, fields: List<FieldSpec>) {
        fields.groupBy { it.paramName }
            .filter { it.value.size > 1 }
            .forEach { (name, items) ->
                logger.error("duplicate parameter '$name'", items.first().property)
            }
        fields.groupBy { it.wireName }
            .filter { it.value.size > 1 }
            .forEach { (name, items) ->
                logger.error(
                    "duplicate SerializedName '$name' in ${declaration.simpleName.asString()}",
                    items.first().property,
                )
            }
    }

    private fun KSClassDeclaration.annotation(qualifiedName: String): KSAnnotation? =
        annotations.firstOrNull { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        }

    private fun wireField(declaration: KSClassDeclaration, property: KSPropertyDeclaration): FieldOverride? {
        val annotation = property.annotations.firstOrNull { item ->
            item.annotationType.resolve().declaration.qualifiedName?.asString() == WIRE_FIELD
        } ?: declaration.primaryConstructor?.parameters?.firstOrNull { parameter ->
            parameter.name?.asString() == property.simpleName.asString()
        }?.annotations?.firstOrNull { item ->
            item.annotationType.resolve().declaration.qualifiedName?.asString() == WIRE_FIELD
        } ?: return FieldOverride()
        val mutable = annotation.mutability() ?: return null
        return FieldOverride(
            key = annotation.string("key").orEmpty().takeUnless { it == WIRE_UNSET }.orEmpty(),
            raw = annotation.string("raw").orEmpty(),
            defaultCode = annotation.string("defaultCode").orEmpty(),
            mutable = mutable,
        )
    }

    private fun formatDefault(code: String): String {
        val lines = code.trim().lines()
        if (lines.size == 1) return lines.first()
        return lines.first() + "\n" + lines.drop(1).joinToString("\n") { line ->
            "        " + line.trim()
        }
    }

    private fun KSAnnotation.string(name: String): String? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String

    private fun KSAnnotation.encoderClassName(): String? {
        val raw = arguments.firstOrNull { it.name?.asString() == "nameEncoder" }?.value ?: return null
        val qualified = when (raw) {
            is KSType -> raw.declaration.qualifiedName?.asString()
            else -> raw.toString().substringBefore('@').takeIf { it.contains('.') }
        } ?: return null
        if (qualified == ENCODER_INTERFACE) return null
        return qualified
    }

    private fun KSAnnotation.mutability(): WireMutability? {
        val raw = arguments.firstOrNull { it.name?.asString() == "mutable" }?.value ?: return WireMutability.FOLLOW
        if (raw is WireMutability) return raw
        val entry = when (raw) {
            is KSType -> raw.declaration.simpleName.asString()
            else -> raw.toString().substringAfterLast('.').substringBefore('@')
        }
        return try {
            WireMutability.valueOf(entry)
        } catch (_: IllegalArgumentException) {
            logger.error("unknown WireMutability '$entry'")
            null
        }
    }

    private fun KSAnnotation.rule(): WireNameRule? {
        val raw = arguments.firstOrNull { it.name?.asString() == "nameRule" }?.value ?: return WireNameRule.OPTION
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

    private fun hidesSuperMember(superClass: String, paramName: String): Boolean {
        val parent = resolver.getClassDeclarationByName(superClass) ?: return false
        return parent.getAllProperties().any { property -> property.simpleName.asString() == paramName }
    }

    private fun pick(explicit: String?, fallback: String): String =
        if (explicit == null || explicit == WIRE_UNSET) fallback else explicit

    private companion object {
        const val GEN_MODEL = "com.wiregen.annotation.GenModel"
        const val WIRE_FIELD = "com.wiregen.annotation.WireField"
        const val ENCODER_INTERFACE = "com.wiregen.annotation.WireNameEncoder"
    }
}

private data class FieldOverride(
    val key: String = "",
    val raw: String = "",
    val defaultCode: String = "",
    val mutable: WireMutability = WireMutability.FOLLOW,
)

private data class ModelIdentity(
    val declaration: KSClassDeclaration,
    val originating: KSFile,
    val sourceText: String,
    val sourceQualified: String,
    val packageName: String,
    val className: String,
    val paramPrefix: String,
    val paramSuffix: String,
    val parentQualifiedNames: List<String>,
    val superClause: String,
    val nameRule: String,
    val encoder: WireNameEncoder?,
)

private data class FieldSpec(
    val property: KSPropertyDeclaration,
    val paramName: String,
    val semantic: String,
    val wireName: String,
    val type: KSType,
    val defaultCode: String?,
    val mutable: Boolean,
)

private data class ModelSpec(
    val identity: ModelIdentity,
    val fields: List<FieldSpec>,
)
