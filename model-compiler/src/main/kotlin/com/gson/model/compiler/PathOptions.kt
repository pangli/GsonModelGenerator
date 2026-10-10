package com.gson.model.compiler

import java.io.File
import java.util.Properties

internal object PathOptionKeys {
    const val CLASS_PREFIX = "path.classPrefix"
    const val CLASS_SUFFIX = "path.classSuffix"
    const val PACKAGE_NAME = "path.packageName"
    const val NAME_RULE = "path.nameRule"
    const val NAME_ENCODER = "path.nameEncoder"
    const val XOR_KEY = "path.xorKey"
    const val DICT = "path.dict"
    const val MAPPING_FILE = "path.mappingFile"
    /** `segment`（默认）或 `whole`。 */
    const val ENCODE_MODE = "path.encodeMode"
}

internal data class PathOptions(
    val classPrefix: String,
    val classSuffix: String,
    val packageName: String,
    val nameRule: String,
    val nameEncoder: String,
    val xorKey: String,
    val dictPath: String,
    val mappingFile: String,
    val encodeMode: String,
) {
    fun loadDict(): Map<String, String> {
        if (dictPath.isBlank()) return emptyMap()
        val file = File(dictPath)
        require(file.isFile) { "path.dict not found: $dictPath" }
        val properties = Properties()
        file.reader(Charsets.UTF_8).use(properties::load)
        return properties.entries.associate { entry ->
            entry.key.toString() to entry.value.toString()
        }
    }

    fun mappingEnabled(): Boolean {
        val value = mappingFile.trim()
        return value.isNotEmpty() && !value.equals("false", ignoreCase = true) && value != "0"
    }

    fun mappingUsesCodeGenerator(): Boolean =
        mappingEnabled() && mappingFile.trim().equals("true", ignoreCase = true)

    fun mappingOutputFile(): File? {
        if (!mappingEnabled() || mappingUsesCodeGenerator()) return null
        return File(mappingFile.trim())
    }

    companion object {
        fun from(options: Map<String, String>): PathOptions = PathOptions(
            classPrefix = options.pathOrModel(PathOptionKeys.CLASS_PREFIX, ModelOptionKeys.CLASS_PREFIX),
            classSuffix = options.pathOrModel(PathOptionKeys.CLASS_SUFFIX, ModelOptionKeys.CLASS_SUFFIX),
            packageName = options.pathOrModel(PathOptionKeys.PACKAGE_NAME, ModelOptionKeys.PACKAGE_NAME),
            nameRule = options[PathOptionKeys.NAME_RULE]?.takeIf { it.isNotBlank() } ?: "raw",
            nameEncoder = options[PathOptionKeys.NAME_ENCODER].orEmpty(),
            xorKey = when {
                PathOptionKeys.XOR_KEY in options -> options[PathOptionKeys.XOR_KEY].orEmpty()
                else -> options[ModelOptionKeys.XOR_KEY].orEmpty()
            },
            dictPath = options[PathOptionKeys.DICT].orEmpty(),
            mappingFile = options[PathOptionKeys.MAPPING_FILE].orEmpty(),
            encodeMode = options[PathOptionKeys.ENCODE_MODE]?.takeIf { it.isNotBlank() } ?: "segment",
        )

        private fun Map<String, String>.pathOrModel(pathKey: String, modelKey: String): String =
            if (containsKey(pathKey)) this[pathKey].orEmpty() else this[modelKey].orEmpty()
    }
}
