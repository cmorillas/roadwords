package com.telytec.roadwords.data

data class WordWithProgress(
    val id: Int,
    val english: String,
    val spanish: String,
    val cefrLevel: String,
    val isPhrasalVerb: Boolean,
    val enToEsLevel: Int?,
    val esToEnLevel: Int?,
    val totalReviews: Int?,
    val streak: Int?
)
