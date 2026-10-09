package com.gson.model.compiler

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Variance

internal class TypeRenderer(
    private val generatedBySource: Map<String, String>,
    private val annotatedSources: Set<String>,
    private val targetPackage: String,
) {
    private val bound = linkedMapOf<String, String>()
    private val imported = linkedSetOf<String>()

    val imports: Set<String> get() = imported

    fun render(type: KSType): String {
        if (type.isError) {
            throw IllegalArgumentException("unresolved type")
        }
        val declaration = type.declaration
        val qualified = declaration.qualifiedName?.asString()
            ?: throw IllegalArgumentException("type has no qualified name")
        if (qualified.startsWith("kotlin.Function")) {
            throw IllegalArgumentException("function types are not supported: $qualified")
        }
        if (qualified in annotatedSources && qualified !in generatedBySource) {
            throw IllegalArgumentException("spec $qualified was not generated")
        }
        val raw = generatedBySource[qualified] ?: qualified
        val rendered = if (type.arguments.isEmpty()) {
            reference(raw)
        } else {
            reference(raw) + type.arguments.joinToString(prefix = "<", postfix = ">") { argument ->
                renderArgument(argument)
            }
        }
        return if (type.isMarkedNullable) "$rendered?" else rendered
    }

    fun reference(qualified: String): String {
        val builtin = KOTLIN_SHORT_NAMES[qualified]
        if (builtin != null) {
            bind(builtin, qualified)
            return builtin
        }
        val simple = qualified.substringAfterLast('.')
        val previous = bound[simple]
        if (previous != null && previous != qualified) return qualified
        bound[simple] = qualified
        if (shouldImport(qualified, targetPackage)) imported += qualified
        return simple
    }

    private fun bind(simple: String, qualified: String) {
        val previous = bound[simple]
        if (previous == null || previous == qualified) bound[simple] = qualified
    }

    private fun renderArgument(argument: KSTypeArgument): String {
        val rendered = argument.type?.resolve()?.let(::render)
        return when (argument.variance) {
            Variance.STAR -> "*"
            Variance.COVARIANT -> "out ${rendered ?: throw IllegalArgumentException("missing type argument")}"
            Variance.CONTRAVARIANT -> "in ${rendered ?: throw IllegalArgumentException("missing type argument")}"
            Variance.INVARIANT -> rendered ?: throw IllegalArgumentException("missing type argument")
        }
    }

    companion object {
        fun shouldImport(qualified: String, targetPackage: String): Boolean {
            val pkg = qualified.substringBeforeLast('.', "")
            if (pkg.isEmpty() || pkg == targetPackage) return false
            if (qualified.startsWith("kotlin.")) return false
            if (qualified in KOTLIN_SHORT_NAMES) return false
            return true
        }

        private val KOTLIN_SHORT_NAMES = mapOf(
            "kotlin.String" to "String",
            "kotlin.Int" to "Int",
            "kotlin.Long" to "Long",
            "kotlin.Boolean" to "Boolean",
            "kotlin.Double" to "Double",
            "kotlin.Float" to "Float",
            "kotlin.Short" to "Short",
            "kotlin.Byte" to "Byte",
            "kotlin.Char" to "Char",
            "kotlin.Any" to "Any",
            "kotlin.Unit" to "Unit",
            "kotlin.Nothing" to "Nothing",
            "kotlin.collections.List" to "List",
            "kotlin.collections.MutableList" to "MutableList",
            "kotlin.collections.Set" to "Set",
            "kotlin.collections.MutableSet" to "MutableSet",
            "kotlin.collections.Map" to "Map",
            "kotlin.collections.MutableMap" to "MutableMap",
            "kotlin.collections.Collection" to "Collection",
            "kotlin.collections.Iterable" to "Iterable",
            "kotlin.Array" to "Array",
            "kotlin.IntArray" to "IntArray",
            "kotlin.LongArray" to "LongArray",
            "kotlin.BooleanArray" to "BooleanArray",
            "kotlin.ByteArray" to "ByteArray",
            "kotlin.ShortArray" to "ShortArray",
            "kotlin.FloatArray" to "FloatArray",
            "kotlin.DoubleArray" to "DoubleArray",
            "kotlin.CharArray" to "CharArray",
            "java.lang.String" to "String",
        )
    }
}
