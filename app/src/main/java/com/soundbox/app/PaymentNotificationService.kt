package com.soundbox.app

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

/** Listens to notifications and announces received UPI payments. */
class PaymentNotificationService : NotificationListenerService() {

    companion object {
        // ===== ANCHOR: SUPPORTED_APPS (add more UPI apps: package name to display name) =====
        val APP_NAMES = mapOf(
            "com.phonepe.app" to "PhonePe",
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            // Shopkeeper / business apps (also need a matching <package> in AndroidManifest.xml <queries>)
            "com.google.android.apps.nbu.paisa.merchant" to "Google Pay Business",
            "com.paytm.business" to "Paytm Business",
            "com.phonepe.app.business" to "PhonePe Business",
            "com.bharatpe.app" to "BharatPe",
            "com.mobikwik_new" to "MobiKwik"
        )
        // ===== END ANCHOR: SUPPORTED_APPS =====

        // ===== ANCHOR: AMOUNT_REGEX (original: (?:Rs\.?|₹|INR)\s?(\d+)) =====
        // Same idea, but also handles "1,200" and avoids matching words like "Users 5".
        val AMOUNT_REGEX = Regex("""(?<![A-Za-z])(?:Rs\.?|₹|INR)\s?(\d[\d,]*)""", RegexOption.IGNORE_CASE)
        // ===== END ANCHOR: AMOUNT_REGEX =====

        // ===== ANCHOR: KEYWORDS (what counts as money received / sent) =====
        val POSITIVE = Regex("""\b(received|credited|paid you)\b""", RegexOption.IGNORE_CASE)
        val NEGATIVE = Regex("""\b(sent|debited)\b""", RegexOption.IGNORE_CASE)
        // ===== END ANCHOR: KEYWORDS =====
    }

    private var lastKey = ""
    private var lastTime = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        startKeepAlive()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, PaymentNotificationService::class.java))
            } catch (e: Exception) { /* ignore */ }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val appName = APP_NAMES[sbn.packageName] ?: return
        val extras = sbn.notification?.extras ?: return

        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        ).joinToString(" ")
        if (text.isBlank()) return

        // Only "received / credited", never "sent / debited"
        if (!POSITIVE.containsMatchIn(text) || NEGATIVE.containsMatchIn(text)) return

        val amount = AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.replace(",", "") ?: return

        // Ignore the same notification re-posted within 10 seconds
        val key = "${sbn.packageName}|$text"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastTime < 10_000) return
        lastKey = key
        lastTime = now

        TTSManager.speak(applicationContext, amount, appName)
        AppPrefs.addPayment(applicationContext, Payment(amount, appName, now))
    }

    private fun startKeepAlive() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        } catch (e: Exception) { /* may be blocked in background; MainActivity starts it too */ }
    }
}
