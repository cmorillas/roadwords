package com.telytec.roadwords.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val wordId: Int,
    val enToEsLevel: Int = 0,
    val esToEnLevel: Int = 0,
    val streak: Int = 0,
    val totalReviews: Int = 0,
    val isInActiveRound: Boolean = false,
    val lastSeenTurn: Int = 0,
    val lastFailedTurn: Int = -100 // Cooldown tracking after failure
)
