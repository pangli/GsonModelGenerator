package com.gson.model.compiler

internal data class PathConstantMapping(
    val name: String,
    val semantic: String,
    val wire: String,
)

internal data class PathObjectMapping(
    val source: String,
    val generated: String,
    val paths: List<PathConstantMapping>,
)

/**
 * Semantic ↔ encoded API path pairs for troubleshooting.
 *
 * ```
 * {
 *   "com.example.SparrowApiConstants": {
 *     "source": "com.example.ApiConstants",
 *     "paths": [
 *       { "name": "GET_APP_CONFIG_PATH", "semantic": "api/app/...", "wire": "e/squill/..." }
 *     ]
 *   }
 * }
 * ```
 */
internal object PathMappingJson {
    fun render(objects: List<PathObjectMapping>): String {
        val ordered = objects.sortedBy { it.generated }
        return buildString {
            appendLine("{")
            ordered.forEachIndexed { objectIndex, item ->
                append("  ").append(WireMappingJson.quote(item.generated)).appendLine(": {")
                append("    ").append(WireMappingJson.quote("source")).append(": ")
                    .append(WireMappingJson.quote(item.source)).appendLine(",")
                append("    ").append(WireMappingJson.quote("paths")).appendLine(": [")
                item.paths.forEachIndexed { pathIndex, path ->
                    append("      { ")
                    append(WireMappingJson.quote("name")).append(": ")
                        .append(WireMappingJson.quote(path.name)).append(", ")
                    append(WireMappingJson.quote("semantic")).append(": ")
                        .append(WireMappingJson.quote(path.semantic)).append(", ")
                    append(WireMappingJson.quote("wire")).append(": ")
                        .append(WireMappingJson.quote(path.wire))
                    append(" }")
                    if (pathIndex < item.paths.lastIndex) append(",")
                    appendLine()
                }
                append("    ]")
                appendLine()
                append("  }")
                if (objectIndex < ordered.lastIndex) append(",")
                appendLine()
            }
            append("}")
            appendLine()
        }
    }
}
