package com.wiregen.compiler

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.wiregen.annotation.GenApiConstants
import com.wiregen.annotation.GenModel

class WireProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val writtenModels = mutableSetOf<String>()
    private val writtenPaths = mutableSetOf<String>()
    private val wireMappings = linkedMapOf<String, WireClassMapping>()
    private val pathMappings = linkedMapOf<String, PathObjectMapping>()
    private val wireOrigins = linkedSetOf<KSFile>()
    private val pathOrigins = linkedSetOf<KSFile>()
    private var modelOptions: WireModelOptions? = null
    private var pathOptions: PathOptions? = null

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        deferred += processModels(resolver)
        deferred += processApiConstants(resolver)
        return deferred
    }

    override fun finish() {
        val modelOptions = modelOptions
        if (modelOptions != null && modelOptions.mappingEnabled() && wireMappings.isNotEmpty()) {
            MappingFiles.write(
                environment.codeGenerator,
                environment.logger,
                modelOptions.mappingFile,
                "model-wire-mapping",
                wireOrigins,
                WireMappingJson.render(wireMappings.values.toList()),
                WireModelOptionKeys.MAPPING_FILE,
            )
        }
        val pathOptions = pathOptions
        if (pathOptions != null && pathOptions.mappingEnabled() && pathMappings.isNotEmpty()) {
            MappingFiles.write(
                environment.codeGenerator,
                environment.logger,
                pathOptions.mappingFile,
                "path-mapping",
                pathOrigins,
                PathMappingJson.render(pathMappings.values.toList()),
                PathOptionKeys.MAPPING_FILE,
            )
        }
    }

    private fun processModels(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenModel::class.qualifiedName!!).toList()
        if (symbols.isEmpty()) return emptyList()

        val options = WireModelOptions.from(environment.options)
        modelOptions = options
        val dict = try {
            options.loadDict()
        } catch (error: IllegalArgumentException) {
            environment.logger.error(error.message ?: "failed to load wire.model.dict")
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
            val generated = WireModelGenerator(
                environment.codeGenerator,
                environment.logger,
                resolver,
                options,
                dict,
                writtenModels,
            ).generate(ready)
            if (options.mappingEnabled()) {
                generated.forEach { mapping ->
                    wireMappings[mapping.generated] = mapping
                }
                ready.mapNotNullTo(wireOrigins) { it.containingFile }
            }
        }
        return deferred
    }

    private fun processApiConstants(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenApiConstants::class.qualifiedName!!).toList()
        if (symbols.isEmpty()) return emptyList()

        val options = PathOptions.from(environment.options)
        pathOptions = options
        val dict = try {
            options.loadDict()
        } catch (error: IllegalArgumentException) {
            environment.logger.error(error.message ?: "failed to load wire.path.dict")
            return emptyList()
        }

        val deferred = mutableListOf<KSAnnotated>()
        val ready = mutableListOf<KSClassDeclaration>()
        symbols.forEach { symbol ->
            val declaration = symbol as? KSClassDeclaration
            if (declaration == null) {
                environment.logger.error("@GenApiConstants is only allowed on an object", symbol)
                return@forEach
            }
            if (declaration.classKind != ClassKind.OBJECT) {
                environment.logger.error("@GenApiConstants only supports object declarations", declaration)
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
            val generated = ApiConstantsGenerator(
                environment.codeGenerator,
                environment.logger,
                options,
                dict,
                writtenPaths,
            ).generate(ready)
            if (options.mappingEnabled()) {
                generated.forEach { mapping ->
                    pathMappings[mapping.generated] = mapping
                }
                ready.mapNotNullTo(pathOrigins) { it.containingFile }
            }
        }
        return deferred
    }
}

class WireProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        WireProcessor(environment)
}
