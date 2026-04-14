package com.telytec.roadwords.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val english: String,
    val spanish: String,
    val englishAlts: String = "",   // comma-separated alternative EN translations
    val spanishAlts: String = "",   // comma-separated alternative ES translations
    val cefrLevel: String = "B1",
    val isPhrasalVerb: Boolean = false,
    val partOfSpeech: String = "",
    val category: String = "general",
    val frequencyRank: Int = 0,
    val exampleEn: String = "",
    val exampleEn2: String = "",
    val exampleEn3: String = ""
) {
    fun allEnglish(): List<String> {
        val list = mutableListOf(english)
        if (englishAlts.isNotBlank()) list.addAll(englishAlts.split(",").map { it.trim() })
        return list
    }
    fun allSpanish(): List<String> {
        val list = mutableListOf(spanish)
        if (spanishAlts.isNotBlank()) list.addAll(spanishAlts.split(",").map { it.trim() })
        return list
    }

    fun englishExamples(): List<String> {
        return listOf(exampleEn, exampleEn2, exampleEn3).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun rotatedEnglishExample(seed: Int): String {
        val examples = englishExamples()
        if (examples.isEmpty()) return ""
        return examples[Math.floorMod(seed, examples.size)]
    }
}
