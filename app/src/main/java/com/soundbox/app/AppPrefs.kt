package com.soundbox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One announced payment. */
data class Payment(val amount: String, val appName: String, val timeMillis: Long)

/** SharedPreferences helper: language choice + last 10 payments. */
object AppPrefs {
    const val FILE = "soundbox_prefs"
    const val MAX_HISTORY = 10
    private const val KEY_LANG = "lang"          // "en" or "hi"
    private const val KEY_HISTORY = "history"

    fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLang(ctx: Context): String = prefs(ctx).getString(KEY_LANG, "en") ?: "en"

    fun setLang(ctx: Context, lang: String) {
        prefs(ctx).edit().putString(KEY_LANG, lang).apply()
    }

    /** Checklist items that Android cannot verify (autostart, restricted settings). */
    fun getFlag(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean("flag_$key", false)

    fun setFlag(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean("flag_$key", value).apply()
    }

    @Synchronized
    fun addPayment(ctx: Context, payment: Payment) {
        val list = getHistory(ctx).toMutableList()
        list.add(0, payment)
        val arr = JSONArray()
        list.take(MAX_HISTORY).forEach {
            arr.put(
                JSONObject()
                    .put("amount", it.amount)
                    .put("app", it.appName)
                    .put("time", it.timeMillis)
            )
        }
        prefs(ctx).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun getHistory(ctx: Context): List<Payment> {
        val raw = prefs(ctx).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Payment(o.getString("amount"), o.getString("app"), o.getLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
