package com.telytec.roadwords.domain

import com.telytec.roadwords.data.ProgressEntity

/**
 * Circular-flow learning engine.
 *
 * Rules:
 * - EN→ES starts first (recognition). Need 3 correct to graduate that direction.
 * - ES→EN unlocks after the first EN→ES hit. Need 2 correct to graduate.
 * - When both graduated → word is LEARNED.
 * - On fail: level drops by 1 (min 0), streak resets, word enters a short cooldown.
 * - On correct: level++, streak++, cooldown cleared.
 * - Production unlocks after the first recognition hit, then direction is weighted by remaining work.
 * - Mastered words return with 10% probability as "review cards" in random direction.
 */
object SrsEngine {

    const val EN_TO_ES_THRESHOLD = 3
    const val ES_TO_EN_THRESHOLD = 2
    const val ROUND_SIZE = 6
    const val REVIEW_CHANCE = 0.10f
    const val FAILURE_COOLDOWN_TURNS = 2
    const val MIN_TURNS_BETWEEN_REPEATS = 2

    /**
     * Determine which direction to test this word in.
     *
     * - Brand new word (totalReviews == 0): always EN→ES (recognition first).
     * - Mastered word (review card): 50/50 random.
     * - In training: ES→EN unlocks after the first EN→ES success, then both directions
     *   compete by remaining work. This starts production early without asking it cold.
     */
    fun getDirection(progress: ProgressEntity): String? {
        val enRemaining = maxOf(0, EN_TO_ES_THRESHOLD - progress.enToEsLevel)
        val esRemaining = maxOf(0, ES_TO_EN_THRESHOLD - progress.esToEnLevel)
        val enIncomplete = enRemaining > 0
        val esIncomplete = esRemaining > 0

        // Fully mastered → 50/50 random for review
        if (!enIncomplete && !esIncomplete) {
            return if (Math.random() < 0.5) "en_to_es" else "es_to_en"
        }

        // Brand new word, or no recognition hit yet → always start with recognition
        if (progress.totalReviews == 0 || progress.enToEsLevel == 0) return "en_to_es"

        if (enIncomplete && !esIncomplete) return "en_to_es"
        if (!enIncomplete && esIncomplete) return "es_to_en"

        val productionUnlock = progress.enToEsLevel.toDouble() / EN_TO_ES_THRESHOLD
        val enWeight = enRemaining.toDouble()
        val esWeight = esRemaining.toDouble() * productionUnlock
        val total = enWeight + esWeight
        if (total <= 0.0) return "en_to_es"

        return if (Math.random() * total < enWeight) "en_to_es" else "es_to_en"
    }

    fun isLearned(progress: ProgressEntity): Boolean {
        return progress.enToEsLevel >= EN_TO_ES_THRESHOLD && progress.esToEnLevel >= ES_TO_EN_THRESHOLD
    }

    fun applyCorrect(progress: ProgressEntity, direction: String, turn: Int): ProgressEntity {
        val newEn = if (direction == "en_to_es") {
            minOf(EN_TO_ES_THRESHOLD, progress.enToEsLevel + 1)
        } else {
            progress.enToEsLevel
        }
        val newEs = if (direction == "es_to_en") {
            minOf(ES_TO_EN_THRESHOLD, progress.esToEnLevel + 1)
        } else {
            progress.esToEnLevel
        }
        return progress.copy(
            enToEsLevel = newEn,
            esToEnLevel = newEs,
            streak = progress.streak + 1,
            totalReviews = progress.totalReviews + 1,
            lastSeenTurn = turn,
            lastFailedTurn = -100 // clear cooldown
        )
    }

    fun applyFail(progress: ProgressEntity, direction: String, turn: Int): ProgressEntity {
        val newEn = if (direction == "en_to_es") maxOf(0, progress.enToEsLevel - 1) else progress.enToEsLevel
        val newEs = if (direction == "es_to_en") maxOf(0, progress.esToEnLevel - 1) else progress.esToEnLevel
        return progress.copy(
            enToEsLevel = newEn,
            esToEnLevel = newEs,
            streak = 0,
            totalReviews = progress.totalReviews + 1,
            lastSeenTurn = turn,
            lastFailedTurn = turn // start short cooldown
        )
    }
}
