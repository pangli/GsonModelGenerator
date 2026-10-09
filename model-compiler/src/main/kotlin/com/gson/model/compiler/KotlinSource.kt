package com.gson.model.compiler

/**
 * 从规格源码里取出 KSP 不提供的文本：父类构造调用、属性默认值、import。
 */
internal class KotlinSource(private val text: String) {
    fun explicitImports(): Map<String, String> {
        val imports = linkedMapOf<String, String>()
        IMPORT.findAll(stripComments(text)).forEach { match ->
            val qualified = match.groupValues[1]
            if (qualified.endsWith(".*")) return@forEach
            val alias = match.groupValues[2].ifBlank { qualified.substringAfterLast('.') }
            imports[alias] = qualified
        }
        return imports
    }

    fun supertypeClause(typeName: String): String? {
        val span = declarationSpan(typeName) ?: return null
        var index = span.first + typeName.length
        index = skipTrivia(index)
        if (index < text.length && text[index] == '<') {
            index = skipBalanced(index, '<', '>')
            index = skipTrivia(index)
        }
        if (index < text.length && text[index] == '(') {
            index = skipBalanced(index, '(', ')')
            index = skipTrivia(index)
        }
        if (index >= text.length || text[index] != ':') return null
        index = skipTrivia(index + 1)
        val start = index
        var paren = 0
        var brace = 0
        var bracket = 0
        var angle = 0
        while (index < span.last) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                index = skipped
                continue
            }
            if (index >= span.last) break
            when (text[index]) {
                '(' -> paren++
                ')' -> if (paren == 0) return cleanClause(text.substring(start, index)) else paren--
                '{' -> {
                    if (paren == 0 && brace == 0 && bracket == 0 && angle == 0) {
                        return cleanClause(text.substring(start, index))
                    }
                    brace++
                }
                '\n', '\r' -> {
                    if (paren == 0 && brace == 0 && bracket == 0 && angle == 0) {
                        return cleanClause(text.substring(start, index))
                    }
                }
                '}' -> if (brace > 0) brace--
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '<' -> if (isGenericOpen(index)) angle++
                '>' -> if (angle > 0) angle--
            }
            index++
        }
        return cleanClause(text.substring(start, index.coerceAtMost(span.last)))
    }

    fun memberDefault(typeName: String, memberName: String): String? {
        val span = declarationSpan(typeName) ?: return null
        val nameEnd = findMemberNameEnd(span.first, span.last, memberName) ?: return null
        return readDefault(nameEnd, span.last)
    }

    fun simpleIdentifiers(expression: String): Set<String> {
        val names = linkedSetOf<String>()
        var index = 0
        var afterDot = false
        while (index < expression.length) {
            val skipped = skipStringOrComment(expression, index)
            if (skipped != null) {
                afterDot = false
                index = skipped
                continue
            }
            val char = expression[index]
            if (char.isJavaIdentifierStart()) {
                val ident = readIdent(expression, index)
                if (!afterDot && ident !in KEYWORDS) names += ident
                afterDot = false
                index += ident.length
                continue
            }
            afterDot = char == '.'
            index++
        }
        return names
    }

    private fun declarationSpan(typeName: String): IntRange? {
        val nameAt = findDeclaration(typeName) ?: return null
        var index = nameAt
        var brace = 0
        var started = false
        while (index < text.length) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                index = skipped
                continue
            }
            when (text[index]) {
                '{' -> {
                    brace++
                    started = true
                }
                '}' -> {
                    brace--
                    if (started && brace == 0) return nameAt until index
                }
            }
            index++
        }
        return nameAt until text.length
    }

    private fun findDeclaration(typeName: String): Int? {
        var index = 0
        while (index < text.length) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                index = skipped
                continue
            }
            val word = peekIdent(index)
            if (word == "class" || word == "interface" || word == "object") {
                val nameAt = skipTrivia(index + word.length)
                if (peekIdent(nameAt) == typeName) return nameAt
            }
            index += if (word != null) word.length else 1
        }
        return null
    }

    private fun findMemberNameEnd(start: Int, end: Int, memberName: String): Int? {
        var index = start
        while (index < end) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                index = skipped
                continue
            }
            val word = peekIdent(index)
            if (word == "val" || word == "var") {
                val nameAt = skipTrivia(index + word.length)
                if (peekIdent(nameAt) == memberName) return nameAt + memberName.length
            }
            index += if (word != null) word.length else 1
        }
        return null
    }

    private fun readDefault(start: Int, end: Int): String? {
        var index = skipTrivia(start)
        var paren = 0
        var bracket = 0
        var brace = 0
        var angle = 0
        while (index < end) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                index = skipped
                continue
            }
            val char = text[index]
            val top = paren == 0 && bracket == 0 && brace == 0 && angle == 0
            if (top && char == '=') {
                return readExpression(skipTrivia(index + 1), end)
            }
            if (top && (char == ',' || char == ')' || char == '{')) return null
            when (char) {
                '(' -> paren++
                ')' -> if (paren > 0) paren-- else return null
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                '<' -> if (isGenericOpen(index)) angle++
                '>' -> if (angle > 0) angle--
            }
            index++
        }
        return null
    }

    private fun readExpression(start: Int, end: Int): String {
        var index = start
        var paren = 0
        var bracket = 0
        var brace = 0
        var angle = 0
        while (index < end) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                if (paren == 0 && bracket == 0 && brace == 0 && angle == 0 &&
                    index < text.length && text.startsWith("//", index)
                ) {
                    break
                }
                index = skipped
                continue
            }
            val char = text[index]
            val top = paren == 0 && bracket == 0 && brace == 0 && angle == 0
            if (top && (char == ',' || char == ')' || char == '{')) break
            if (top && (char == '\n' || char == '\r')) {
                val next = skipTrivia(index)
                if (next >= end || text[next] != '.' && text[next] != '?' && !isContinuation(text[next])) break
            }
            when (char) {
                '(' -> paren++
                ')' -> if (paren > 0) paren-- else break
                '[' -> bracket++
                ']' -> if (bracket > 0) bracket--
                '{' -> brace++
                '}' -> if (brace > 0) brace--
                '<' -> if (isGenericOpen(index)) angle++
                '>' -> if (angle > 0) angle--
            }
            index++
        }
        return text.substring(start, index).trim()
    }

    private fun isGenericOpen(index: Int): Boolean {
        if (index > 0 && text[index - 1].isWhitespace()) return false
        var previous = index - 1
        while (previous >= 0 && text[previous].isWhitespace()) previous--
        if (previous < 0) return false
        val char = text[previous]
        return char.isJavaIdentifierPart() || char == '.' || char == '?' || char == ')'
    }

    private fun isContinuation(char: Char): Boolean = char == '+' || char == '.' || char == '?'

    private fun skipBalanced(start: Int, open: Char, close: Char): Int {
        var index = start + 1
        var depth = 1
        while (index < text.length && depth > 0) {
            val skipped = skipStringOrComment(index)
            if (skipped != null) {
                index = skipped
                continue
            }
            when (text[index]) {
                open -> depth++
                close -> depth--
            }
            index++
        }
        return index
    }

    private fun skipTrivia(start: Int): Int {
        var index = start
        while (index < text.length) {
            val skipped = skipStringOrComment(index)
            if (skipped != null && (text.startsWith("//", index) || text.startsWith("/*", index) || text[index].isWhitespace())) {
                index = skipped
                continue
            }
            if (text[index].isWhitespace()) {
                index++
                continue
            }
            break
        }
        return index
    }

    private fun skipStringOrComment(index: Int): Int? = skipStringOrComment(text, index)

    private fun peekIdent(index: Int): String? {
        if (index >= text.length || !text[index].isJavaIdentifierStart()) return null
        return readIdent(text, index)
    }

    private fun cleanClause(raw: String): String? = raw.trim().trimEnd(',', ' ').ifBlank { null }

    companion object {
        private val IMPORT = Regex("""(?m)^import\s+(\S+)(?:\s+as\s+([A-Za-z_]\w*))?\s*$""")
        private val KEYWORDS = setOf(
            "val", "var", "null", "true", "false", "this", "super", "if", "else", "when",
            "as", "is", "in", "object", "return", "throw", "try", "catch", "finally",
            "class", "interface", "fun", "package", "import", "by", "get", "set",
            "constructor", "init", "where", "typeof", "break", "continue",
        )

        private fun stripComments(text: String): String = buildString {
            var index = 0
            while (index < text.length) {
                val skipped = skipStringOrComment(text, index)
                if (skipped != null && (text.startsWith("//", index) || text.startsWith("/*", index))) {
                    repeat(skipped - index) { append(' ') }
                    index = skipped
                    continue
                }
                if (skipped != null) {
                    append(text.substring(index, skipped))
                    index = skipped
                    continue
                }
                append(text[index])
                index++
            }
        }

        private fun skipStringOrComment(text: String, index: Int): Int? {
            if (index >= text.length) return null
            if (text.startsWith("\"\"\"", index)) {
                val end = text.indexOf("\"\"\"", index + 3)
                return if (end < 0) text.length else end + 3
            }
            if (text[index] == '"') return skipQuoted(text, index, '"')
            if (text[index] == '\'') return skipQuoted(text, index, '\'')
            if (text.startsWith("//", index)) {
                val end = text.indexOf('\n', index + 2)
                return if (end < 0) text.length else end
            }
            if (text.startsWith("/*", index)) {
                val end = text.indexOf("*/", index + 2)
                return if (end < 0) text.length else end + 2
            }
            return null
        }

        private fun skipQuoted(text: String, start: Int, quote: Char): Int {
            var index = start + 1
            while (index < text.length) {
                if (text[index] == '\\') {
                    index += 2
                    continue
                }
                if (text[index] == quote) return index + 1
                index++
            }
            return text.length
        }

        private fun readIdent(text: String, index: Int): String {
            var end = index + 1
            while (end < text.length && text[end].isJavaIdentifierPart()) end++
            return text.substring(index, end)
        }
    }
}
