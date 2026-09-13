package com.jovi.photoai.data.reference

internal enum class KnowledgeBundleJsonProblem { MALFORMED, DUPLICATE_KEY, TOO_DEEP, INVALID_UNICODE }

/**
 * Syntax preflight for the bounded, image-free v1 document, not a replacement for schema/digest
 * validation. Android's JSONTokener deliberately accepts non-JSON syntax. Validate one complete
 * RFC 8259 object before giving the same text to JSONObject. No input content is logged.
 * Unpaired UTF-16 surrogates are rejected so canonical UTF-8 hashing cannot silently replace them.
 */
internal object StrictKnowledgeBundleJson {
    private const val MAX_DEPTH = 32

    fun problem(text: String): KnowledgeBundleJsonProblem? = try {
        Reader(text).document()
        null
    } catch (invalid: InvalidJson) {
        invalid.problem
    }

    private class InvalidJson(val problem: KnowledgeBundleJsonProblem) : RuntimeException()

    private class Reader(private val text: String) {
        private var position = 0

        fun document() {
            whitespace()
            if (peek() != '{') fail()
            value(0)
            whitespace()
            if (position != text.length) fail()
        }

        private fun value(depth: Int) {
            whitespace()
            when (peek()) {
                '{' -> {
                    if (depth >= MAX_DEPTH) fail(KnowledgeBundleJsonProblem.TOO_DEEP)
                    objectValue(depth + 1)
                }
                '[' -> {
                    if (depth >= MAX_DEPTH) fail(KnowledgeBundleJsonProblem.TOO_DEEP)
                    arrayValue(depth + 1)
                }
                '"' -> stringValue()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', in '0'..'9' -> numberValue()
                else -> fail()
            }
        }

        private fun objectValue(depth: Int) {
            expect('{')
            whitespace()
            if (take('}')) return
            val keys = mutableSetOf<String>()
            while (true) {
                whitespace()
                val key = stringValue()
                if (!keys.add(key)) fail(KnowledgeBundleJsonProblem.DUPLICATE_KEY)
                whitespace()
                expect(':')
                value(depth)
                whitespace()
                if (take('}')) return
                expect(',')
            }
        }

        private fun arrayValue(depth: Int) {
            expect('[')
            whitespace()
            if (take(']')) return
            while (true) {
                value(depth)
                whitespace()
                if (take(']')) return
                expect(',')
            }
        }

        private fun stringValue(): String {
            expect('"')
            val decoded = StringBuilder()
            while (position < text.length) {
                val character = text[position++]
                when {
                    character == '"' -> return decoded.toString().also(::validateUnicode)
                    character == '\\' -> {
                        if (position >= text.length) fail()
                        when (val escaped = text[position++]) {
                            '"', '\\', '/' -> decoded.append(escaped)
                            'b' -> decoded.append('\b')
                            'f' -> decoded.append('\u000c')
                            'n' -> decoded.append('\n')
                            'r' -> decoded.append('\r')
                            't' -> decoded.append('\t')
                            'u' -> {
                                var code = 0
                                repeat(4) {
                                    if (position >= text.length) fail()
                                    val digit = when (val hex = text[position++]) {
                                        in '0'..'9' -> hex - '0'
                                        in 'a'..'f' -> hex - 'a' + 10
                                        in 'A'..'F' -> hex - 'A' + 10
                                        else -> fail()
                                    }
                                    code = code * 16 + digit
                                }
                                decoded.append(code.toChar())
                            }
                            else -> fail()
                        }
                    }
                    character < ' ' -> fail()
                    else -> decoded.append(character)
                }
            }
            fail()
        }

        private fun validateUnicode(value: String) {
            var index = 0
            while (index < value.length) {
                val character = value[index++]
                if (Character.isHighSurrogate(character)) {
                    if (index >= value.length || !Character.isLowSurrogate(value[index++])) {
                        fail(KnowledgeBundleJsonProblem.INVALID_UNICODE)
                    }
                } else if (Character.isLowSurrogate(character)) {
                    fail(KnowledgeBundleJsonProblem.INVALID_UNICODE)
                }
            }
        }

        private fun numberValue() {
            take('-')
            if (!take('0')) {
                if (peek() !in '1'..'9') fail()
                position++
                digits()
            }
            if (take('.')) {
                if (peek() !in '0'..'9') fail()
                digits()
            }
            if (take('e') || take('E')) {
                if (!take('+')) take('-')
                if (peek() !in '0'..'9') fail()
                digits()
            }
        }

        private fun digits() {
            while (peek() in '0'..'9') position++
        }

        private fun literal(expected: String) {
            if (!text.startsWith(expected, position)) fail()
            position += expected.length
        }

        private fun whitespace() {
            while (position < text.length && text[position] in " \t\r\n") position++
        }

        private fun peek(): Char = text.getOrNull(position) ?: '\u0000'

        private fun take(character: Char): Boolean {
            if (position < text.length && text[position] == character) {
                position++
                return true
            }
            return false
        }

        private fun expect(character: Char) {
            if (!take(character)) fail()
        }

        private fun fail(problem: KnowledgeBundleJsonProblem = KnowledgeBundleJsonProblem.MALFORMED): Nothing =
            throw InvalidJson(problem)
    }
}
