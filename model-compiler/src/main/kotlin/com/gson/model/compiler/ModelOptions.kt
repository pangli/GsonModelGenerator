package com.gson.model.compiler

import java.io.File
import java.util.Properties

internal object ModelOptionKeys {
    const val CLASS_PREFIX = "model.classPrefix"
    const val CLASS_SUFFIX = "model.classSuffix"
    const val PARAM_PREFIX = "model.paramPrefix"
    const val PARAM_SUFFIX = "model.paramSuffix"
    const val SUPER_CLASS = "model.superClass"
    const val SUPER_ARGS = "model.superArgs"
    const val PACKAGE_NAME = "model.packageName"
    const val NAME_RULE = "model.nameRule"
    const val NAME_ENCODER = "model.nameEncoder"
    const val XOR_KEY = "model.xorKey"
    const val DICT = "model.dict"
    /** `true` → KSP resources；绝对路径 → 该文件；空 / `false` → 关闭。 */
    const val MAPPING_FILE = "model.mappingFile"
}

internal data class ModelOptions(
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
        require(file.isFile) { "model.dict not found: $dictPath" }
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
        fun from(options: Map<String, String>): ModelOptions = ModelOptions(
            classPrefix = options[ModelOptionKeys.CLASS_PREFIX].orEmpty(),
            classSuffix = options[ModelOptionKeys.CLASS_SUFFIX].orEmpty(),
            paramPrefix = options[ModelOptionKeys.PARAM_PREFIX].orEmpty(),
            paramSuffix = options[ModelOptionKeys.PARAM_SUFFIX].orEmpty(),
            superClass = options[ModelOptionKeys.SUPER_CLASS].orEmpty(),
            superArgs = options[ModelOptionKeys.SUPER_ARGS].orEmpty(),
            packageName = options[ModelOptionKeys.PACKAGE_NAME].orEmpty(),
            nameRule = options[ModelOptionKeys.NAME_RULE]?.takeIf { it.isNotBlank() } ?: "raw",
            nameEncoder = options[ModelOptionKeys.NAME_ENCODER].orEmpty(),
            xorKey = options[ModelOptionKeys.XOR_KEY].orEmpty(),
            dictPath = options[ModelOptionKeys.DICT].orEmpty(),
            mappingFile = options[ModelOptionKeys.MAPPING_FILE].orEmpty(),
        )
    }
}
