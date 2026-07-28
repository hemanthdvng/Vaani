package com.hemanth.vaani.call

import android.content.Context
import android.provider.ContactsContract
import com.hemanth.vaani.data.SpamNumberEntity
import com.hemanth.vaani.data.VaaniDao

/**
 * Local, offline spam heuristic. No network calls -- everything here runs
 * on-device so screening works even with no connectivity.
 *
 * Score is 0.0 (definitely legitimate) to 1.0 (definitely spam).
 * Threshold for silencing is applied by the caller (VaaniCallScreeningService).
 */
class SpamScorer(
    private val context: Context,
    private val dao: VaaniDao
) {

    data class Result(val score: Float, val reason: String, val isContact: Boolean)

    suspend fun score(rawNumber: String): Result {
        val number = normalize(rawNumber)

        if (isKnownContact(number)) {
            return Result(0f, "In contacts", isContact = true)
        }

        dao.findWhitelistEntry(number)?.let {
            return Result(0f, "Whitelisted (${it.label})", isContact = true)
        }

        dao.findSpamEntry(number)?.let { entry ->
            // Reported spam numbers get a score proportional to report count,
            // capped so a single report doesn't instantly nuke a number.
            val score = (0.5f + entry.reportCount * 0.1f).coerceAtMost(1f)
            return Result(score, "Reported spam x${entry.reportCount}", isContact = false)
        }

        // Heuristics for numbers with no history at all.
        var score = 0.2f // baseline: unknown number, mildly suspicious
        val reasons = mutableListOf<String>()

        if (isLikelyTelemarketerPattern(number)) {
            score += 0.35f
            reasons += "matches telemarketer prefix pattern"
        }
        if (number.length > 13) {
            score += 0.15f
            reasons += "unusually long/international number"
        }

        return Result(score.coerceAtMost(1f), reasons.joinToString(", ").ifEmpty { "unknown number" }, isContact = false)
    }

    private fun normalize(number: String): String =
        number.filter { it.isDigit() || it == '+' }

    private fun isKnownContact(number: String): Boolean {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { cursor -> return cursor.count > 0 }
        return false
    }

    // Placeholder heuristic: known Indian telemarketer/spam number series
    // (140xxxxxxx = DLT-registered promotional/service calls).
    private fun isLikelyTelemarketerPattern(number: String): Boolean {
        val digitsOnly = number.trimStart('+')
        return digitsOnly.startsWith("140") || digitsOnly.startsWith("91140")
    }

    suspend fun reportAsSpam(number: String) {
        val normalized = normalize(number)
        val existing = dao.findSpamEntry(normalized)
        dao.upsertSpamEntry(
            SpamNumberEntity(
                phoneNumber = normalized,
                reportCount = (existing?.reportCount ?: 0) + 1,
                lastReportedMillis = System.currentTimeMillis(),
                source = "user_reported"
            )
        )
    }
}
