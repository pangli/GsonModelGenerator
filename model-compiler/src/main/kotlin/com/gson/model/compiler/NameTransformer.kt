package com.gson.model.compiler

import java.util.Base64

internal object NameTransformer {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val qualified = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    fun className(simpleName: String, prefix: String, suffix: String): String =
        prefix + simpleName + suffix

    fun paramName(name: String, prefix: String, suffix: String): String {
        val core = if (prefix.isEmpty()) {
            name
        } else {
            name.replaceFirstChar { char ->
                if (char.isLowerCase()) char.uppercaseChar() else char
            }
        }
        return prefix + core + suffix
    }

    fun wireName(
        semantic: String,
        rule: String,
        dict: Map<String, String>,
        xorKey: String,
    ): String {
        require(semantic.isNotBlank()) { "semantic name is blank" }
        return when (rule.lowercase()) {
            "raw" -> semantic
            "base64" -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(semantic.toByteArray(Charsets.UTF_8))
            "reverse" -> semantic.reversed()
            "xor" -> {
                require(xorKey.isNotEmpty()) { "name rule xor requires ksp arg model.xorKey" }
                xorHex(semantic, xorKey)
            }
            "dict" -> dict[semantic]
                ?: throw IllegalArgumentException("model.dict has no entry for '$semantic'")
            else -> throw IllegalArgumentException("unknown model.nameRule '$rule'")
        }
    }

    fun isIdentifier(name: String): Boolean = identifier.matches(name)

    fun isQualifiedName(name: String): Boolean = qualified.matches(name)

    fun kotlinStringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(char)
            }
        }
        append('"')
    }

    private fun xorHex(value: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        return value.toByteArray(Charsets.UTF_8)
            .mapIndexed { index, byte -> byte.toInt() xor keyBytes[index % keyBytes.size].toInt() }
            .joinToString("") { byte -> "%02x".format(byte and 0xff) }
    }
}
