package com.telytec.roadwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWord(id: Int): WordEntity?

    @Query("SELECT * FROM progress WHERE wordId = :wordId")
    suspend fun getProgress(wordId: Int): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: ProgressEntity)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getWordCount(): Int

    // ── Mazo (Batch) Management ──

    @Query("SELECT COUNT(*) FROM progress WHERE isInActiveRound = 1")
    suspend fun getActiveRoundCount(): Int

    // Get NON-GRADUATED words from the mazo, respecting failure cooldown.
    // Final ordering is handled in WordRepository with weighted urgency.
    @Query("""
        SELECT w.* FROM words w
        INNER JOIN progress p ON w.id = p.wordId
        WHERE p.isInActiveRound = 1
        AND (p.enToEsLevel < :enThreshold OR p.esToEnLevel < :esThreshold)
        AND (:currentTurn - p.lastFailedTurn >= :cooldownTurns)
        ORDER BY
            p.lastSeenTurn ASC,
            (p.enToEsLevel + p.esToEnLevel) ASC
    """)
    suspend fun getMazoWordsToStudy(
        currentTurn: Int,
        enThreshold: Int,
        esThreshold: Int,
        cooldownTurns: Int
    ): List<WordEntity>

    // Get NON-GRADUATED words ignoring cooldown (fallback when all are in cooldown)
    @Query("""
        SELECT w.* FROM words w
        INNER JOIN progress p ON w.id = p.wordId
        WHERE p.isInActiveRound = 1
        AND (p.enToEsLevel < :enThreshold OR p.esToEnLevel < :esThreshold)
        ORDER BY
            p.lastSeenTurn ASC,
            (p.enToEsLevel + p.esToEnLevel) ASC
    """)
    suspend fun getMazoWordsToStudyNoCooldown(enThreshold: Int, esThreshold: Int): List<WordEntity>

    // Count how many words in the mazo are graduated (both directions complete)
    @Query("""
        SELECT COUNT(*) FROM progress
        WHERE isInActiveRound = 1
        AND enToEsLevel >= :enThreshold
        AND esToEnLevel >= :esThreshold
    """)
    suspend fun getMazoGraduatedCount(enThreshold: Int, esThreshold: Int): Int

    // Deactivate all active round words (called when mazo is complete)
    @Query("UPDATE progress SET isInActiveRound = 0 WHERE isInActiveRound = 1")
    suspend fun deactivateAllRound()

    // Pick words never seen (no progress entry), filtered by level
    @Query("""
        SELECT w.id FROM words w
        WHERE w.cefrLevel IN (:levels)
        AND NOT EXISTS (SELECT 1 FROM progress p WHERE p.wordId = w.id)
        ORDER BY w.frequencyRank ASC
    """)
    suspend fun getAllFreshWordIds(levels: List<String>): List<Int>

    // Pick words with lowest progress that are not in active round and not fully learned
    @Query("""
        SELECT w.id FROM words w
        LEFT JOIN progress p ON w.id = p.wordId
        WHERE w.cefrLevel IN (:levels)
        AND COALESCE(p.isInActiveRound, 0) = 0
        AND (COALESCE(p.enToEsLevel, 0) < :enThreshold OR COALESCE(p.esToEnLevel, 0) < :esThreshold)
        ORDER BY (COALESCE(p.enToEsLevel, 0) + COALESCE(p.esToEnLevel, 0)) ASC, w.frequencyRank ASC
        LIMIT :limit
    """)
    suspend fun getUnlearnedWordIds(levels: List<String>, enThreshold: Int, esThreshold: Int, limit: Int): List<Int>

    // Pick a random learned word for review (not in active round)
    @Query("""
        SELECT w.* FROM words w
        INNER JOIN progress p ON w.id = p.wordId
        WHERE p.isInActiveRound = 0
        AND p.enToEsLevel >= :enThreshold
        AND p.esToEnLevel >= :esThreshold
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomLearnedWord(enThreshold: Int, esThreshold: Int): WordEntity?

    // ── Stats ──

    @Query("""
        SELECT w.id, w.english, w.spanish, w.cefrLevel, w.isPhrasalVerb,
               p.enToEsLevel, p.esToEnLevel, p.totalReviews, p.streak
        FROM words w
        LEFT JOIN progress p ON w.id = p.wordId
        WHERE w.cefrLevel IN (:levels)
        ORDER BY w.cefrLevel ASC, w.english ASC
    """)
    suspend fun getWordsWithProgress(levels: List<String>): List<WordWithProgress>

    @Query("SELECT COUNT(*) FROM words WHERE cefrLevel IN (:levels)")
    suspend fun getWordCountForLevels(levels: List<String>): Int

    @Query("""
        SELECT COUNT(*) FROM words w
        INNER JOIN progress p ON w.id = p.wordId
        WHERE w.cefrLevel IN (:levels)
        AND p.enToEsLevel >= :enThreshold AND p.esToEnLevel >= :esThreshold
    """)
    suspend fun getLearnedCountForLevels(levels: List<String>, enThreshold: Int, esThreshold: Int): Int
}
