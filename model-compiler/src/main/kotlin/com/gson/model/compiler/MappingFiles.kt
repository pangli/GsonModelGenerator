package com.gson.model.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.io.FileAlreadyExistsException

internal object MappingFiles {
    fun write(
        codeGenerator: CodeGenerator,
        logger: KSPLogger,
        mappingFileOption: String,
        defaultResourceName: String,
        origins: Collection<KSFile>,
        json: String,
        optionLabel: String,
    ) {
        val trimmed = mappingFileOption.trim()
        val enabled = trimmed.isNotEmpty() &&
            !trimmed.equals("false", ignoreCase = true) &&
            trimmed != "0"
        if (!enabled) return

        if (!trimmed.equals("true", ignoreCase = true)) {
            val path = File(trimmed)
            try {
                path.parentFile?.mkdirs()
                path.writeText(json, Charsets.UTF_8)
            } catch (error: Exception) {
                logger.error("failed to write $optionLabel ${path.absolutePath}: ${error.message}")
            }
            return
        }

        val files = origins.toTypedArray()
        try {
            codeGenerator.createNewFile(
                Dependencies(aggregating = true, *files),
                "",
                defaultResourceName,
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
}
