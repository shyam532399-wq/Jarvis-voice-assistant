package com.jarvis.assistant

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var geminiKey: String
        get() = sp.getString("gemini_key", "") ?: ""
        set(v) { sp.edit().putString("gemini_key", v.trim()).apply() }

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { sp.edit().putString("model", v.trim()).apply() }

    var porcupineKey: String
        get() = sp.getString("porcupine_key", "") ?: ""
        set(v) { sp.edit().putString("porcupine_key", v.trim()).apply() }

    var followUpSeconds: Int
        get() = sp.getInt("follow_up", 30)
        set(v) { sp.edit().putInt("follow_up", v).apply() }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}
