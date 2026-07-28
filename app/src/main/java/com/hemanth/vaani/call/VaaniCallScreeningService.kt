package com.hemanth.vaani.call

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.hemanth.vaani.data.CallLogEntity
import com.hemanth.vaani.data.ScreeningOutcome
import com.hemanth.vaani.data.VaaniDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Registered via ROLE_CALL_SCREENING (RoleManager) -- no need to become the
 * default dialer. The user grants this role once from MainActivity.
 *
 * Threshold: score >= SPAM_THRESHOLD -> silence + notify.
 *            score <  SPAM_THRESHOLD -> allow to ring normally.
 */
class VaaniCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "VaaniCallScreening"
        private const val SPAM_THRESHOLD = 0.55f
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: run {
            respondAllow(callDetails, allow = true)
            return
        }

        serviceScope.launch {
            val dao = VaaniDatabase.getInstance(applicationContext).vaaniDao()
            val scorer = SpamScorer(applicationContext, dao)
            val result = scorer.score(number)

            val isSpam = result.score >= SPAM_THRESHOLD && !result.isContact
            val outcome = when {
                result.isContact -> ScreeningOutcome.WHITELISTED
                isSpam -> ScreeningOutcome.SILENCED_SPAM
                else -> ScreeningOutcome.ALLOWED
            }

            dao.insertCallLog(
                CallLogEntity(
                    phoneNumber = number,
                    timestampMillis = System.currentTimeMillis(),
                    outcome = outcome,
                    spamScore = result.score,
                    reason = result.reason
                )
            )

            if (isSpam) {
                Log.i(TAG, "Silencing $number (score=${result.score}, reason=${result.reason})")
                respondSilence(callDetails)
                CallNotificationHelper.notifySilencedSpamCall(applicationContext, number, result.reason)
            } else {
                respondAllow(callDetails, allow = true)
            }
        }
    }

    private fun respondAllow(details: Call.Details, allow: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(!allow)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(details, response)
    }

    private fun respondSilence(details: Call.Details) {
        // Disallow = call is rejected from the user's perspective (no ring),
        // but we keep it out of the system call log ourselves so our own
        // Room-backed log is the source of truth, and we show our own
        // notification instead of the system's missed-call one.
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(true)
            .setSkipNotification(true)
            .build()
        respondToCall(details, response)
    }
}
