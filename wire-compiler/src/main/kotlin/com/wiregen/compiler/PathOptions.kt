package com.wiregen.compiler

import java.io.File
import java.util.Properties

internal object PathOptionKeys {
    const val CLASS_PREFIX = "wire.path.classPrefix"
    const val CLASS_SUFFIX = "wire.path.classSuffix"
    const val PACKAGE_NAME = "wire.path.packageName"
    const val NAME_RULE = "wire.path.nameRule"
    const val NAME_ENCODER = "wire.path.nameEncoder"
    const val XOR_KEY = "wire.path.xorKey"
    const val DICT = "wire.path.dict"
    const val MAPPING_FILE = "wire.path.mappingFile"
    /** `segment`（默认）或 `whole`。 */
    const val ENCODE_MODE = "wire.path.encodeMode"
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
        require(file.isFile) { "wire.path.dict not found: $dictPath" }
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
            classPrefix = options.pathOrShared(PathOptionKeys.CLASS_PREFIX, WireModelOptionKeys.CLASS_PREFIX),
            classSuffix = options.pathOrShared(PathOptionKeys.CLASS_SUFFIX, WireModelOptionKeys.CLASS_SUFFIX),
            packageName = options.pathOrShared(PathOptionKeys.PACKAGE_NAME, WireModelOptionKeys.PACKAGE_NAME),
            nameRule = options[PathOptionKeys.NAME_RULE]?.takeIf { it.isNotBlank() } ?: "raw",
            nameEncoder = options[PathOptionKeys.NAME_ENCODER].orEmpty(),
            xorKey = when {
                PathOptionKeys.XOR_KEY in options -> options[PathOptionKeys.XOR_KEY].orEmpty()
                else -> options[WireModelOptionKeys.XOR_KEY].orEmpty()
            },
            dictPath = options[PathOptionKeys.DICT].orEmpty(),
            mappingFile = options[PathOptionKeys.MAPPING_FILE].orEmpty(),
            encodeMode = options[PathOptionKeys.ENCODE_MODE]?.takeIf { it.isNotBlank() } ?: "segment",
        )

        private fun Map<String, String>.pathOrShared(pathKey: String, sharedKey: String): String =
            if (containsKey(pathKey)) this[pathKey].orEmpty() else this[sharedKey].orEmpty()
    }
}
