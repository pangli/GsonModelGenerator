package com.wiregen.compiler

import java.io.File
import java.util.Properties

internal object WireModelOptionKeys {
    const val CLASS_PREFIX = "wire.classPrefix"
    const val CLASS_SUFFIX = "wire.classSuffix"
    const val PACKAGE_NAME = "wire.packageName"
    const val PARAM_PREFIX = "wire.model.paramPrefix"
    const val PARAM_SUFFIX = "wire.model.paramSuffix"
    const val SUPER_CLASS = "wire.model.superClass"
    const val SUPER_ARGS = "wire.model.superArgs"
    const val NAME_RULE = "wire.model.nameRule"
    const val NAME_ENCODER = "wire.model.nameEncoder"
    const val XOR_KEY = "wire.model.xorKey"
    const val DICT = "wire.model.dict"
    /** `true` → KSP resources；绝对路径 → 该文件；空 / `false` → 关闭。 */
    const val MAPPING_FILE = "wire.model.mappingFile"
}

internal data class WireModelOptions(
    val classPrefix: String,
    val classSuffix: String,
    val paramPrefix: String,
    val paramSuffix: String,
    val superClass: String,
    val superArgs: String,
    val packageName: String,
    val nameRule: String,
    val nameEncoder: String,
    val xorKey: String,
    val dictPath: String,
    /** Blank / false = off. `true` = KSP resources. Otherwise an absolute JSON path. */
    val mappingFile: String,
) {
    fun loadDict(): Map<String, String> {
        if (dictPath.isBlank()) return emptyMap()
        val file = File(dictPath)
        require(file.isFile) { "wire.model.dict not found: $dictPath" }
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
        fun from(options: Map<String, String>): WireModelOptions = WireModelOptions(
            classPrefix = options[WireModelOptionKeys.CLASS_PREFIX].orEmpty(),
            classSuffix = options[WireModelOptionKeys.CLASS_SUFFIX].orEmpty(),
            paramPrefix = options[WireModelOptionKeys.PARAM_PREFIX].orEmpty(),
            paramSuffix = options[WireModelOptionKeys.PARAM_SUFFIX].orEmpty(),
            superClass = options[WireModelOptionKeys.SUPER_CLASS].orEmpty(),
            superArgs = options[WireModelOptionKeys.SUPER_ARGS].orEmpty(),
            packageName = options[WireModelOptionKeys.PACKAGE_NAME].orEmpty(),
            nameRule = options[WireModelOptionKeys.NAME_RULE]?.takeIf { it.isNotBlank() } ?: "raw",
            nameEncoder = options[WireModelOptionKeys.NAME_ENCODER].orEmpty(),
            xorKey = options[WireModelOptionKeys.XOR_KEY].orEmpty(),
            dictPath = options[WireModelOptionKeys.DICT].orEmpty(),
            mappingFile = options[WireModelOptionKeys.MAPPING_FILE].orEmpty(),
        )
    }
}
