package com.soundbox.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Foreground service: shows a small ongoing notification so Android keeps the app alive. */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "soundbox_running"
        private const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Soundbox running", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // If notification access is OFF the app is deaf: say so, tap opens the checklist
        val accessOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val text = if (accessOn) "Listening for UPI payments. Keep PhonePe / GPay / Paytm notifications ON."
                   else "⚠ Notification access is OFF. Tap to open the setup checklist."
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_speaker)
            .setContentTitle("Payment Soundbox is running")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_STICKY
    }
}
