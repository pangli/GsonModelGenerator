package com.gson.model.compiler

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.gson.model.annotation.GenModel

class ModelProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val written = mutableSetOf<String>()
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenModel::class.qualifiedName!!).toList()
        if (symbols.isEmpty()) return emptyList()

        val options = ModelOptions.from(environment.options)
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
            ModelGenerator(
                environment.codeGenerator,
                environment.logger,
                resolver,
                options,
                dict,
                written,
            ).generate(ready)
        }
        return deferred
    }
}

class ModelProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ModelProcessor(environment)
}
