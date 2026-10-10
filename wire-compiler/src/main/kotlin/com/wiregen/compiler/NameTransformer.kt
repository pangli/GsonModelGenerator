package com.wiregen.compiler

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
                require(xorKey.isNotEmpty()) { "name rule xor requires ksp arg wire.model.xorKey or wire.path.xorKey" }
                xorHex(semantic, xorKey)
            }
            "dict" -> dict[semantic]
                ?: throw IllegalArgumentException("dict has no entry for '$semantic'")
            else -> throw IllegalArgumentException("unknown nameRule '$rule'")
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

    /** Parses a Kotlin double-quoted string literal expression into its content. */
    fun parseKotlinStringLiteral(expression: String): String? {
        val trimmed = expression.trim()
        if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) return null
        val body = trimmed.substring(1, trimmed.lastIndex)
        return buildString {
            var index = 0
            while (index < body.length) {
                val char = body[index]
                if (char != '\\') {
                    append(char)
                    index++
                    continue
                }
                if (index + 1 >= body.length) return null
                when (val next = body[index + 1]) {
                    '\\', '"', '$' -> {
                        append(next)
                        index += 2
                    }
                    'n' -> {
                        append('\n')
                        index += 2
                    }
                    'r' -> {
                        append('\r')
                        index += 2
                    }
                    't' -> {
                        append('\t')
                        index += 2
                    }
                    'u' -> {
                        if (index + 5 >= body.length) return null
                        val hex = body.substring(index + 2, index + 6)
                        val code = hex.toIntOrNull(16) ?: return null
                        append(code.toChar())
                        index += 6
                    }
                    else -> return null
                }
            }
        }
    }

    /**
     * Encodes an API path. [mode] `segment` (default) runs [encode] on each `/` piece;
     * `whole` runs it once on the full path. Empty segments (leading/trailing `//`) are kept.
     */
    fun encodePath(path: String, mode: String, encode: (String) -> String): String {
        return when (mode.lowercase()) {
            "whole" -> encode(path)
            "segment" -> splitPathSegments(path).joinToString("/") { piece ->
                if (piece.isEmpty()) piece else encode(piece)
            }
            else -> throw IllegalArgumentException("unknown wire.path.encodeMode '$mode'")
        }
    }

    /** Like `split('/')` but keeps leading, trailing, and empty segments. */
    fun splitPathSegments(path: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (index in path.indices) {
            if (path[index] == '/') {
                parts += path.substring(start, index)
                start = index + 1
            }
        }
        parts += path.substring(start)
        return parts
    }

    private fun xorHex(value: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        return value.toByteArray(Charsets.UTF_8)
            .mapIndexed { index, byte -> byte.toInt() xor keyBytes[index % keyBytes.size].toInt() }
            .joinToString("") { byte -> "%02x".format(byte and 0xff) }
    }
}
