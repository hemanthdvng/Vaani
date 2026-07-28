package com.hemanth.vaani.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ScreeningOutcome { ALLOWED, SILENCED_SPAM, WHITELISTED }

@Entity(tableName = "call_log")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val timestampMillis: Long,
    val outcome: ScreeningOutcome,
    val spamScore: Float,
    val reason: String
)
