package io.ingwe.dash.data.obd

/**
 * A signal formula ("((A*256)+B)/4") parsed ONCE into a small expression tree.
 *
 * Why: the poller evaluates ~20 formulas around 10 times a second. Re-parsing the
 * text each time (string replace + tokenise + parse) allocates and burns CPU on a
 * weak head unit. A compiled tree evaluates with a handful of virtual calls and no
 * allocation at all.
 *
 * Variables A, B, C, D … are response data bytes 0, 1, 2, 3 … (unsigned).
 */
class Formula private constructor(
    private val root: Node,
    /** Highest byte index the formula reads, or -1 if it reads none. */
    val maxByteIndex: Int,
    val source: String,
) {
    /** Returns null when the response is too short for this formula, or the result isn't finite. */
    fun evaluate(bytes: ByteArray): Double? {
        if (bytes.size <= maxByteIndex) return null
        val v = root.eval(bytes)
        return if (v.isFinite()) v else null
    }

    override fun toString() = source

    companion object {
        /** @throws IllegalArgumentException if the formula is malformed. */
        fun compile(source: String): Formula {
            val parser = Parser(source.replace(" ", ""))
            val root = parser.parseExpr()
            require(parser.atEnd()) { "Unexpected '${parser.rest()}' in formula '$source'" }
            return Formula(root, parser.maxVar, source)
        }
    }

    private sealed interface Node { fun eval(b: ByteArray): Double }
    private class Const(val v: Double) : Node { override fun eval(b: ByteArray) = v }
    private class Var(val i: Int) : Node { override fun eval(b: ByteArray) = (b[i].toInt() and 0xFF).toDouble() }
    private class Neg(val n: Node) : Node { override fun eval(b: ByteArray) = -n.eval(b) }
    private class Add(val l: Node, val r: Node) : Node { override fun eval(b: ByteArray) = l.eval(b) + r.eval(b) }
    private class Sub(val l: Node, val r: Node) : Node { override fun eval(b: ByteArray) = l.eval(b) - r.eval(b) }
    private class Mul(val l: Node, val r: Node) : Node { override fun eval(b: ByteArray) = l.eval(b) * r.eval(b) }
    private class Div(val l: Node, val r: Node) : Node { override fun eval(b: ByteArray) = l.eval(b) / r.eval(b) }

    /** Recursive-descent parser: expr = term (± term)*, term = unary (×÷ unary)*, unary = -unary | primary. */
    private class Parser(private val s: String) {
        private var i = 0
        var maxVar = -1
            private set

        fun atEnd() = i >= s.length
        fun rest() = s.substring(i)

        fun parseExpr(): Node {
            var left = parseTerm()
            while (!atEnd() && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val right = parseTerm()
                left = if (op == '+') Add(left, right) else Sub(left, right)
            }
            return left
        }

        private fun parseTerm(): Node {
            var left = parseUnary()
            while (!atEnd() && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val right = parseUnary()
                left = if (op == '*') Mul(left, right) else Div(left, right)
            }
            return left
        }

        private fun parseUnary(): Node {
            if (!atEnd() && s[i] == '-') { i++; return Neg(parseUnary()) }
            return parsePrimary()
        }

        private fun parsePrimary(): Node {
            require(!atEnd()) { "Unexpected end of formula '$s'" }
            val c = s[i]
            return when {
                c == '(' -> {
                    i++
                    val inner = parseExpr()
                    require(!atEnd() && s[i] == ')') { "Missing ')' in formula '$s'" }
                    i++
                    inner
                }
                c in 'A'..'Z' -> {
                    i++
                    val index = c - 'A'
                    if (index > maxVar) maxVar = index
                    Var(index)
                }
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (!atEnd() && (s[i].isDigit() || s[i] == '.')) i++
                    Const(requireNotNull(s.substring(start, i).toDoubleOrNull()) { "Bad number in '$s'" })
                }
                else -> throw IllegalArgumentException("Unexpected '$c' in formula '$s'")
            }
        }
    }
}
