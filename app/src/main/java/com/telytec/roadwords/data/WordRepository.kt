package com.telytec.roadwords.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.telytec.roadwords.domain.SrsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WordRepository(private val context: Context, private val wordDao: WordDao) {

    suspend fun updateProgress(progress: ProgressEntity) {
        withContext(Dispatchers.IO) { wordDao.updateProgress(progress) }
    }

    suspend fun getProgressForWord(wordId: Int): ProgressEntity {
        return withContext(Dispatchers.IO) { wordDao.getProgress(wordId) ?: ProgressEntity(wordId) }
    }

    suspend fun getWordsWithProgress(levels: List<String>): List<WordWithProgress> {
        return withContext(Dispatchers.IO) { wordDao.getWordsWithProgress(levels) }
    }

    suspend fun getWordCountForLevels(levels: List<String>): Int {
        return withContext(Dispatchers.IO) { wordDao.getWordCountForLevels(levels) }
    }

    suspend fun getLearnedCount(levels: List<String>): Int {
        return withContext(Dispatchers.IO) {
            wordDao.getLearnedCountForLevels(levels, SrsEngine.EN_TO_ES_THRESHOLD, SrsEngine.ES_TO_EN_THRESHOLD)
        }
    }

    // ── Mazo (Batch) Logic ──

    /**
     * Get the next word to study from the current mazo.
     * Does NOT graduate words out of the mazo — they stay until the whole mazo is complete.
     * Selection is weighted by remaining work, spacing, and recent failures.
     * Returns null if the mazo is fully graduated (caller should check isMazoComplete).
     */
    suspend fun getNextMazoWord(levels: List<String>, currentTurn: Int): WordEntity? = withContext(Dispatchers.IO) {
        // Ensure the mazo has ROUND_SIZE words
        ensureMazoLoaded(levels)

        // Get non-graduated words respecting cooldown
        val candidates = wordDao.getMazoWordsToStudy(
            currentTurn,
            SrsEngine.EN_TO_ES_THRESHOLD,
            SrsEngine.ES_TO_EN_THRESHOLD,
            SrsEngine.FAILURE_COOLDOWN_TURNS
        )

        selectWeightedMazoCandidate(candidates, currentTurn)?.let { return@withContext it }

        // If all non-graduated words are in cooldown, ignore cooldown
        val fallback = wordDao.getMazoWordsToStudyNoCooldown(
            SrsEngine.EN_TO_ES_THRESHOLD, SrsEngine.ES_TO_EN_THRESHOLD
        )

        return@withContext selectWeightedMazoCandidate(fallback, currentTurn) // null means mazo is complete
    }

    /**
     * Check if the current mazo is fully graduated (all active words mastered).
     */
    suspend fun isMazoComplete(): Boolean = withContext(Dispatchers.IO) {
        val activeCount = wordDao.getActiveRoundCount()
        if (activeCount == 0) return@withContext false // no mazo loaded yet
        val graduatedCount = wordDao.getMazoGraduatedCount(
            SrsEngine.EN_TO_ES_THRESHOLD, SrsEngine.ES_TO_EN_THRESHOLD
        )
        graduatedCount >= activeCount
    }

    /**
     * Get mazo progress: (graduated, total)
     */
    suspend fun getMazoProgress(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val total = wordDao.getActiveRoundCount()
        val graduated = wordDao.getMazoGraduatedCount(
            SrsEngine.EN_TO_ES_THRESHOLD, SrsEngine.ES_TO_EN_THRESHOLD
        )
        Pair(graduated, total)
    }

    /**
     * Complete the current mazo and start a new one.
     */
    suspend fun startNewMazo(levels: List<String>) = withContext(Dispatchers.IO) {
        wordDao.deactivateAllRound()
        ensureMazoLoaded(levels)
    }

    /**
     * Get a random learned word for review (bonus card between mazos or 10% chance within).
     */
    suspend fun getReviewWord(): WordEntity? = withContext(Dispatchers.IO) {
        wordDao.getRandomLearnedWord(SrsEngine.EN_TO_ES_THRESHOLD, SrsEngine.ES_TO_EN_THRESHOLD)
    }

    private suspend fun ensureMazoLoaded(levels: List<String>) {
        val currentActive = wordDao.getActiveRoundCount()
        if (currentActive >= SrsEngine.ROUND_SIZE) return

        val needed = SrsEngine.ROUND_SIZE - currentActive

        // First: pick fresh (never seen) words based on balanced strategy
        val allFresh = wordDao.getAllFreshWordIds(levels)
        val freshIds = selectWithBalanceStrategy(allFresh, needed)
        
        for (id in freshIds) {
            wordDao.updateProgress(ProgressEntity(wordId = id, isInActiveRound = true))
        }

        // If still need more, pick partially-learned ones that are not in the mazo
        val remaining = SrsEngine.ROUND_SIZE - wordDao.getActiveRoundCount()
        if (remaining > 0) {
            val unlearnedIds = wordDao.getUnlearnedWordIds(
                levels, SrsEngine.EN_TO_ES_THRESHOLD, SrsEngine.ES_TO_EN_THRESHOLD, remaining
            )
            for (id in unlearnedIds) {
                val existing = wordDao.getProgress(id)
                if (existing != null) {
                    wordDao.updateProgress(existing.copy(isInActiveRound = true))
                } else {
                    wordDao.updateProgress(ProgressEntity(wordId = id, isInActiveRound = true))
                }
            }
        }
    }

    private fun selectWithBalanceStrategy(all: List<Int>, n: Int): List<Int> {
        if (all.size <= n) return all
        
        val result = mutableListOf<Int>()
        
        // Split into terciles
        val third = all.size / 3
        val g1 = all.subList(0, third).toMutableList()
        val g2 = all.subList(third, third * 2).toMutableList()
        val g3 = all.subList(third * 2, all.size).toMutableList()
        
        val takeProportional = { list: MutableList<Int>, count: Int ->
            val actualCount = minOf(list.size, count)
            val picked = list.take(actualCount)
            list.removeAll(picked)
            result.addAll(picked)
        }

        // Proportions for n=6: (2, 2, 1, 1-rand)
        // For arbitrary n, we allocate roughly 33%, 33%, 16%, 16%
        val nG1 = (n * 0.34).toInt().coerceAtLeast(1)
        val nG2 = (n * 0.34).toInt().coerceAtLeast(1)
        val nG3 = (n * 0.16).toInt().coerceAtLeast(0)
        
        takeProportional(g1, nG1)
        takeProportional(g2, nG2)
        takeProportional(g3, nG3)
        
        // Fill remaining with random from leftovers
        val remainingAll = (g1 + g2 + g3).shuffled()
        if (result.size < n && remainingAll.isNotEmpty()) {
            result.addAll(remainingAll.take(n - result.size))
        }
        
        return result.take(n)
    }

    private suspend fun selectWeightedMazoCandidate(candidates: List<WordEntity>, currentTurn: Int): WordEntity? {
        if (candidates.isEmpty()) return null

        val withProgress = candidates.map { word ->
            val progress = wordDao.getProgress(word.id) ?: ProgressEntity(word.id)
            word to progress
        }

        val spacedCandidates = withProgress.filter { (_, progress) ->
            progress.totalReviews == 0 ||
                currentTurn - progress.lastSeenTurn >= SrsEngine.MIN_TURNS_BETWEEN_REPEATS
        }
        val pool = spacedCandidates.ifEmpty { withProgress }

        val weighted = pool.map { (word, progress) ->
            word to urgencyScore(progress, currentTurn)
        }
        val totalWeight = weighted.sumOf { it.second }
        if (totalWeight <= 0.0) return pool.first().first

        var cursor = Math.random() * totalWeight
        for ((word, weight) in weighted) {
            cursor -= weight
            if (cursor <= 0.0) return word
        }

        return weighted.last().first
    }

    private fun urgencyScore(progress: ProgressEntity, currentTurn: Int): Double {
        val enRemaining = maxOf(0, SrsEngine.EN_TO_ES_THRESHOLD - progress.enToEsLevel)
        val esRemaining = maxOf(0, SrsEngine.ES_TO_EN_THRESHOLD - progress.esToEnLevel)
        val deficit = enRemaining + esRemaining
        if (deficit <= 0) return 0.0

        val turnsSinceSeen = if (progress.totalReviews == 0) {
            SrsEngine.MIN_TURNS_BETWEEN_REPEATS
        } else {
            maxOf(0, currentTurn - progress.lastSeenTurn)
        }

        val failureBoost = if (progress.lastFailedTurn > 0) {
            val turnsSinceFail = currentTurn - progress.lastFailedTurn
            if (turnsSinceFail >= SrsEngine.FAILURE_COOLDOWN_TURNS) 15.0 else 0.0
        } else {
            0.0
        }

        return deficit * 10.0 + minOf(turnsSinceSeen, 5) * 2.0 + failureBoost
    }

    suspend fun seedDatabase() {
        withContext(Dispatchers.IO) {
            if (wordDao.getWordCount() > 0) return@withContext

            val assetName = "vocabulary.db"
            val dbFile = File(context.cacheDir, "temp_vocab.db")

            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val externalDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = externalDb.rawQuery("SELECT * FROM words", null)

                val words = mutableListOf<WordEntity>()
                while (cursor.moveToNext()) {
                    words.add(WordEntity(
                        english = cursor.getString(cursor.getColumnIndexOrThrow("english")),
                        spanish = cursor.getString(cursor.getColumnIndexOrThrow("spanish")),
                        spanishAlts = cursor.getString(cursor.getColumnIndexOrThrow("spanish_alts")),
                        cefrLevel = cursor.getString(cursor.getColumnIndexOrThrow("cefr_level")),
                        isPhrasalVerb = cursor.getInt(cursor.getColumnIndexOrThrow("is_phrasal_verb")) == 1,
                        partOfSpeech = cursor.getString(cursor.getColumnIndexOrThrow("part_of_speech")),
                        category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                        frequencyRank = cursor.getInt(cursor.getColumnIndexOrThrow("frequency_rank")),
                        exampleEn = cursor.getString(cursor.getColumnIndexOrThrow("example_en")),
                        exampleEn2 = cursor.getOptionalString("example_en_2"),
                        exampleEn3 = cursor.getOptionalString("example_en_3")
                    ))

                    if (words.size >= 100) {
                        wordDao.insertWords(words.toList())
                        words.clear()
                    }
                }
                if (words.isNotEmpty()) {
                    wordDao.insertWords(words)
                }

                cursor.close()
                externalDb.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (dbFile.exists()) dbFile.delete()
            }
        }
    }

    private fun Cursor.getOptionalString(columnName: String): String {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else ""
    }
}
