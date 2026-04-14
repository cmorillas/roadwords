package com.telytec.roadwords.domain

import java.util.Locale
import kotlin.math.min

object StringEvaluator {
    /**
     * Check if userInput matches ANY of the expected alternatives.
     * Returns the matched alternative or null.
     */
    fun evaluateAgainstList(userInput: String, alternatives: List<String>): EvalResult {
        val input = userInput.trim().lowercase(Locale.getDefault())
        if (input.isEmpty()) return EvalResult(false, alternatives.first(), Int.MAX_VALUE)

        var bestMatch = alternatives.first()
        var bestDistance = Int.MAX_VALUE

        for (alt in alternatives) {
            val exp = alt.trim().lowercase(Locale.getDefault())
            if (input == exp) return EvalResult(true, alt, 0)
            val d = levenshtein(input, exp)
            if (d < bestDistance) {
                bestDistance = d
                bestMatch = alt
            }
        }

        // Threshold: short words allow 1 typo, longer words allow 2
        val shortestAlt = alternatives.minOf { it.trim().length }
        val threshold = if (shortestAlt <= 4) 1 else 2
        return EvalResult(bestDistance <= threshold, bestMatch, bestDistance)
    }

    fun isCloseAnswer(userInput: String, expected: String): Boolean {
        return evaluateAgainstList(userInput, listOf(expected)).isCorrect
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val dp = Array(lhs.length + 1) { IntArray(rhs.length + 1) }
        for (i in 0..lhs.length) dp[i][0] = i
        for (j in 0..rhs.length) dp[0][j] = j
        for (i in 1..lhs.length) {
            for (j in 1..rhs.length) {
                val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[lhs.length][rhs.length]
    }
}

data class EvalResult(
    val isCorrect: Boolean,
    val closestMatch: String,
    val distance: Int
)
