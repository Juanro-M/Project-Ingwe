package io.ingwe.dash.data.obd

/**
 * Minimal arithmetic expression evaluator for OBD signal formulae.
 *
 * Supports: +, -, *, /, parentheses, integer and decimal literals.
 * No variables — caller substitutes A/B/C/D before calling [eval].
 *
 * Examples:
 *   "((256*0D)+24)/4"   → 850.0  (RPM)
 *   "125-40"            → 85.0   (coolant temp)
 *
 * Not a general scripting engine — only handles the patterns that appear
 * in OBD-II formula definitions.
 */
object SimpleExprEval {

    fun eval(expr: String): Double {
        val tokens = tokenize(expr.replace(" ", ""))
        val parser = Parser(tokens)
        return parser.parseExpr()
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            when {
                expr[i].isDigit() || (expr[i] == '.' && i + 1 < expr.length && expr[i+1].isDigit()) -> {
                    val sb = StringBuilder()
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) sb.append(expr[i++])
                    tokens.add(sb.toString())
                }
                expr[i] in "+-*/().".toSet() -> tokens.add(expr[i++].toString())
                else -> i++ // skip unknown
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        fun parseExpr(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < tokens.size && tokens[pos] in listOf("+", "-")) {
                val op = tokens[pos++]
                val right = parseMulDiv()
                left = if (op == "+") left + right else left - right
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (pos < tokens.size && tokens[pos] in listOf("*", "/")) {
                val op = tokens[pos++]
                val right = parseUnary()
                left = if (op == "*") left * right else left / right
            }
            return left
        }

        private fun parseUnary(): Double {
            if (pos < tokens.size && tokens[pos] == "-") { pos++; return -parsePrimary() }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (pos >= tokens.size) return 0.0
            if (tokens[pos] == "(") {
                pos++ // consume "("
                val result = parseExpr()
                if (pos < tokens.size && tokens[pos] == ")") pos++ // consume ")"
                return result
            }
            return tokens[pos++].toDoubleOrNull() ?: 0.0
        }
    }
}
