package com.gson.model.compiler

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.gson.model.annotation.GenModel
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.io.FileAlreadyExistsException

class ModelProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val written = mutableSetOf<String>()
    private val mappingsByGenerated = linkedMapOf<String, WireClassMapping>()
    private val mappingOrigins = linkedSetOf<KSFile>()
    private var options: ModelOptions? = null

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenModel::class.qualifiedName!!).toList()
        if (symbols.isEmpty()) return emptyList()

        val options = ModelOptions.from(environment.options)
        this.options = options
        val dict = try {
            options.loadDict()
        } catch (error: IllegalArgumentException) {
            environment.logger.error(error.message ?: "failed to load model.dict")
            return emptyList()
        }

        val deferred = mutableListOf<KSAnnotated>()
        val ready = mutableListOf<KSClassDeclaration>()
        symbols.forEach { symbol ->
            val declaration = symbol as? KSClassDeclaration
            if (declaration == null) {
                environment.logger.error("@GenModel is only allowed on a class or interface", symbol)
                return@forEach
            }
            if (declaration.classKind != ClassKind.CLASS && declaration.classKind != ClassKind.INTERFACE) {
                environment.logger.error("@GenModel only supports classes and interfaces", declaration)
                return@forEach
            }
            val unresolved = declaration.getDeclaredProperties().any { property ->
                property.type.resolve().isError
            }
            if (unresolved) {
                deferred += declaration
            } else {
                ready += declaration
            }
        }
        if (ready.isNotEmpty()) {
            val generated = ModelGenerator(
                environment.codeGenerator,
                environment.logger,
                resolver,
                options,
                dict,
                written,
            ).generate(ready)
            if (options.mappingEnabled()) {
                generated.forEach { mapping ->
                    mappingsByGenerated[mapping.generated] = mapping
                }
                ready.mapNotNullTo(mappingOrigins) { it.containingFile }
            }
        }
        return deferred
    }

    override fun finish() {
        val options = options ?: return
        if (!options.mappingEnabled() || mappingsByGenerated.isEmpty()) return
        val json = WireMappingJson.render(mappingsByGenerated.values.toList())
        val path = options.mappingOutputFile()
        if (path != null) {
            try {
                path.parentFile?.mkdirs()
                path.writeText(json, Charsets.UTF_8)
            } catch (error: Exception) {
                environment.logger.error(
                    "failed to write model.mappingFile ${path.absolutePath}: ${error.message}",
                )
            }
            return
        }
        val origins = mappingOrigins.toTypedArray()
        try {
            environment.codeGenerator.createNewFile(
                Dependencies(aggregating = true, *origins),
                "",
                DEFAULT_MAPPING_NAME,
                "json",
            ).use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            }
        } catch (error: FileAlreadyExistsException) {
            error.file.writeText(json)
        }
    }

    private companion object {
        const val DEFAULT_MAPPING_NAME = "model-wire-mapping"
    }
}

class ModelProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ModelProcessor(environment)
}
