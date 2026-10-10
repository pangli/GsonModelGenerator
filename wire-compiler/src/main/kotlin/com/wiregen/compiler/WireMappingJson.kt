package com.wiregen.compiler

internal data class WireFieldMapping(
    val semantic: String,
    val param: String,
    val wire: String,
)

internal data class WireClassMapping(
    val source: String,
    val generated: String,
    val fields: List<WireFieldMapping>,
)

/**
 * Builds a stable JSON document of semantic ↔ wire name pairs for troubleshooting.
 *
 * Shape:
 * ```
 * {
 *   "com.example.SparrowDemo": {
 *     "source": "com.example.Demo",
 *     "fields": [
 *       { "semantic": "phone", "param": "phoneBySparrow", "wire": "cGhvbmU" }
 *     ]
 *   }
 * }
 * ```
 */
internal object WireMappingJson {
    fun render(classes: List<WireClassMapping>): String {
        val ordered = classes.sortedBy { it.generated }
        return buildString {
            appendLine("{")
            ordered.forEachIndexed { classIndex, item ->
                append("  ").append(quote(item.generated)).appendLine(": {")
                append("    ").append(quote("source")).append(": ")
                    .append(quote(item.source)).appendLine(",")
                append("    ").append(quote("fields")).appendLine(": [")
                item.fields.forEachIndexed { fieldIndex, field ->
                    append("      { ")
                    append(quote("semantic")).append(": ").append(quote(field.semantic)).append(", ")
                    append(quote("param")).append(": ").append(quote(field.param)).append(", ")
                    append(quote("wire")).append(": ").append(quote(field.wire))
                    append(" }")
                    if (fieldIndex < item.fields.lastIndex) append(",")
                    appendLine()
                }
                append("    ]")
                appendLine()
                append("  }")
                if (classIndex < ordered.lastIndex) append(",")
                appendLine()
            }
            append("}")
            appendLine()
        }
    }

    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    val code = char.code
                    if (code < 0x20) {
                        append("\\u").append("%04x".format(code))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}
