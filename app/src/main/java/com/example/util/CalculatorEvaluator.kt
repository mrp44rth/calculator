package com.example.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object CalculatorEvaluator {
    fun evaluate(expression: String): Double {
        if (expression.isBlank()) return 0.0
        
        // Normalize symbols to standard characters
        val cleanExpr = expression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace(" ", "")
            
        return Parser(cleanExpr).parse()
    }
    
    private class Parser(val input: String) {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < input.length) input[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }
            return x
        }

        // expression = term | expression + term | expression - term
        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) {
                    x += parseTerm() // addition
                } else if (eat('-'.code)) {
                    x -= parseTerm() // subtraction
                } else {
                    return x
                }
            }
        }

        // term = power | term * power | term / power
        fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                if (eat('*'.code)) {
                    x *= parsePower() // multiplication
                } else if (eat('/'.code)) {
                    val divisor = parsePower()
                    if (divisor == 0.0) {
                        throw ArithmeticException("Division by zero")
                    }
                    x /= divisor // division
                } else {
                    return x
                }
            }
        }

        // power = factor | factor ^ factor
        fun parsePower(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('^'.code)) {
                    x = Math.pow(x, parseFactor())
                } else {
                    return x
                }
            }
        }

        // factor = unaryMinus factor | √ factor | '(' expression ')' | number | factor '%'
        fun parseFactor(): Double {
            if (eat('-'.code)) return -parseFactor() // unary minus
            if (eat('+'.code)) return parseFactor()  // unary plus
            
            // Unicode square root symbol check
            if (eat('√'.code)) {
                val factor = parseFactor()
                if (factor < 0.0) {
                    throw ArithmeticException("Square root of negative number")
                }
                return Math.sqrt(factor)
            }

            var x: Double
            val startPos = this.pos
            if (eat('('.code)) { // parentheses
                x = parseExpression()
                eat(')'.code)
            } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) { // numbers
                while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                val numStr = input.substring(startPos, this.pos)
                x = numStr.toDoubleOrNull() ?: 0.0
            } else {
                throw RuntimeException("Unexpected character: " + ch.toChar())
            }

            // Handle percentage as postfix operator on factors
            while (eat('%'.code)) {
                x *= 0.01
            }

            return x
        }
    }
    
    // Format double to beautiful string with no trailing .0 or excessive decimals
    fun formatResult(value: Double): String {
        if (value.isInfinite()) return "Infinity"
        if (value.isNaN()) return "Error"
        
        try {
            // Check if it's an integer
            if (value % 1 == 0.0) {
                if (value >= Long.MAX_VALUE.toDouble() || value <= Long.MIN_VALUE.toDouble()) {
                     return value.toString()
                }
                return value.toLong().toString()
            }
            
            // Format to reasonable decimals
            val bd = BigDecimal(value, MathContext.DECIMAL64)
            val scaled = bd.stripTrailingZeros()
            val scale = scaled.scale()
            return if (scale > 8) {
                val rounded = bd.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                rounded.toPlainString()
            } else {
                scaled.toPlainString()
            }
        } catch (e: Exception) {
            return value.toString()
        }
    }
}
