package com.hemanth.vaani.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hemanth.vaani.R
import kotlin.random.Random

object CallNotificationHelper {

    private const val CHANNEL_ID = "vaani_spam_calls"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Silenced spam calls",
            NotificationManager.IMPORTANCE_LOW // low importance: no sound, just a heads-up in the shade
        ).apply {
            description = "Calls Vaani silently blocked because they looked like spam"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifySilencedSpamCall(context: Context, number: String, reason: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_blocked)
            .setContentTitle("Blocked a likely spam call")
            .setContentText("$number · $reason")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(Random.nextInt(), notification)
    }
}
