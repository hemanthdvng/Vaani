package com.hemanth.vaani.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val reportCount: Int = 1,
    val lastReportedMillis: Long,
    val source: String // "user_reported" | "local_heuristic" | "imported_list"
)
