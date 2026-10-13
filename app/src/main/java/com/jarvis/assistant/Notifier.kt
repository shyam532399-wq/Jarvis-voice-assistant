package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifier {
    const val CH_LISTEN = "jarvis_listening"
    const val CH_ALERT = "jarvis_alert"
    const val ID_LISTEN = 1
    const val ID_ALERT = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val listen = NotificationChannel(CH_LISTEN, "Jarvis listening", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Silent notification while Jarvis is listening"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val alert = NotificationChannel(CH_ALERT, "Jarvis restart", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Silent reminder to reopen Jarvis"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(listen)
        nm.createNotificationChannel(alert)
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun listening(ctx: Context, text: String): Notification {
        val stopIntent = Intent(ctx, JarvisService::class.java).setAction(JarvisService.ACTION_STOP)
        val stop = PendingIntent.getService(
            ctx, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CH_LISTEN)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setContentIntent(openAppIntent(ctx))
            .addAction(0, "Band karo", stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showRestartNotice(ctx: Context) {
        try {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentTitle("Jarvis band hai")
                .setContentText("Tap karo, Jarvis dobara sunna shuru kar dega.")
                .setContentIntent(openAppIntent(ctx))
                .setAutoCancel(true)
                .setSilent(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
        } catch (_: Exception) {
        }
    }

    fun clearRestartNotice(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(ID_ALERT)
        } catch (_: Exception) {
        }
    }
}
