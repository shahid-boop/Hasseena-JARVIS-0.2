package com.hasseena.jarvis

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
    var apiKey: String get() = p.getString("api_key", "") ?: ""; set(v) { p.edit().putString("api_key", v.trim()).apply() }
    var model: String
        get() {
            val saved = p.getString("model", "")?.trim().orEmpty()
            // Migrate the old model automatically so an existing install does not keep
            // calling the unavailable model after updating Hasseena.
            return if (saved.isBlank() || saved == "gemini-2.0-flash" || saved == "gemini-2.0-flash-lite" || saved == "gemini-2.5-flash" || saved.contains("-preview")) {
                AppConfig.DEFAULT_MODEL
            } else {
                saved
            }
        }
        set(v) { p.edit().putString("model", v.trim()).apply() }
    var voiceEnabled: Boolean get() = p.getBoolean("voice_enabled", true); set(v) { p.edit().putBoolean("voice_enabled", v).apply() }
    var backgroundEnabled: Boolean get() = p.getBoolean("background_enabled", false); set(v) { p.edit().putBoolean("background_enabled", v).apply() }
    var memory: String get() = p.getString("memory", "") ?: ""; set(v) { p.edit().putString("memory", v).apply() }
}
