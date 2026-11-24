package io.ingwe.dash.data.obd

/**
 * Minimal arithmetic expression evaluator for OBD signal formulae.
 *
 * Supports: +, -, *, /, parentheses, unary minus, integer and decimal literals.
 * No variables — caller substitutes A/B/C/D before calling [eval].
 *
 * Examples:
 *   "((13*256)+36)/4"   → 841.0  (RPM)
 *   "125-40"            → 85.0   (coolant temp)
 *
 * Not a general scripting engine — only handles the patterns that appear
 * in OBD-II formula definitions. Malformed input (unknown characters,
 * unbalanced parentheses, trailing tokens, division by zero) throws
 * [IllegalArgumentException] instead of silently producing a number.
 */
object SimpleExprEval {

    fun eval(expr: String): Double {
        val tokens = tokenize(expr.replace(" ", ""))
        require(tokens.isNotEmpty()) { "Empty expression" }
        val parser = Parser(tokens)
        val result = parser.parseExpr()
        require(parser.atEnd()) { "Unexpected token '${tokens[parser.position]}' in '$expr'" }
        require(result.isFinite()) { "Non-finite result for '$expr'" }
        return result
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) sb.append(expr[i++])
                    require(sb.toString().toDoubleOrNull() != null) { "Bad number '$sb'" }
                    tokens.add(sb.toString())
                }
                c in "+-*/()" -> { tokens.add(c.toString()); i++ }
                else -> throw IllegalArgumentException("Unexpected character '$c' in '$expr'")
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>) {
        var position = 0
            private set

        fun atEnd() = position >= tokens.size

        fun parseExpr(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (!atEnd() && (tokens[position] == "+" || tokens[position] == "-")) {
                val op = tokens[position++]
                val right = parseMulDiv()
                left = if (op == "+") left + right else left - right
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (!atEnd() && (tokens[position] == "*" || tokens[position] == "/")) {
                val op = tokens[position++]
                val right = parseUnary()
                left = if (op == "*") left * right else left / right
            }
            return left
        }

        private fun parseUnary(): Double {
            if (!atEnd() && tokens[position] == "-") { position++; return -parseUnary() }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            require(!atEnd()) { "Unexpected end of expression" }
            val token = tokens[position++]
            if (token == "(") {
                val result = parseExpr()
                require(!atEnd() && tokens[position] == ")") { "Missing ')'" }
                position++
                return result
            }
            return requireNotNull(token.toDoubleOrNull()) { "Expected number, got '$token'" }
        }
    }
}
