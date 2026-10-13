package com.soundbox.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Text-to-speech announcer. Pushes media volume to MAX while speaking,
 * then restores the previous volume.
 */
object TTSManager {

    // ===== ANCHOR: SPOKEN_MESSAGES (edit what the app says) =====
    private fun message(lang: String, amount: String, appName: String): String =
        if (lang == "hi") "$appName से $amount रुपये प्राप्त हुए"
        else "$amount rupees received from $appName"
    // ===== END ANCHOR: SPOKEN_MESSAGES =====

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Runnable>()
    private val activeCount = AtomicInteger(0)
    private var savedVolume = -1

    @Synchronized
    fun init(ctx: Context) {
        if (tts != null) return
        val app = ctx.applicationContext
        tts = TextToSpeech(app) { status ->
            synchronized(this) {
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    while (pending.isNotEmpty()) pending.removeFirst().run()
                }
            }
        }
    }

    @Synchronized
    fun speak(ctx: Context, amount: String, appName: String) {
        init(ctx)
        val app = ctx.applicationContext
        val job = Runnable { doSpeak(app, amount, appName) }
        if (ready) job.run() else pending.add(job)
    }

    private fun doSpeak(ctx: Context, amount: String, appName: String) {
        val engine = tts ?: return
        var lang = AppPrefs.getLang(ctx)

        val locale = if (lang == "hi") Locale("hi", "IN") else Locale("en", "IN")
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Voice not installed -> fall back to English
            engine.setLanguage(Locale.ENGLISH)
            lang = "en"
        }

        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (activeCount.get() == 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
            )
        } catch (e: Exception) { /* ignore */ }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished(am)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(am)
        })

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        activeCount.incrementAndGet()
        engine.speak(
            message(lang, amount, appName),
            TextToSpeech.QUEUE_ADD,
            params,
            "payment_${System.nanoTime()}"
        )
    }

    private fun finished(am: AudioManager) {
        if (activeCount.decrementAndGet() <= 0) {
            activeCount.set(0)
            try {
                if (savedVolume >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            } catch (e: Exception) { /* ignore */ }
            savedVolume = -1
        }
    }
}
