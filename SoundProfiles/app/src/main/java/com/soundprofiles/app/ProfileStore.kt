package com.soundprofiles.app

import android.content.Context

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("sound_profiles_v1", Context.MODE_PRIVATE)

    fun load(id: ProfileId): SoundProfile {
        val defaults = defaultProfile(id)
        val prefix = id.name.lowercase()
        return SoundProfile(
            ring = prefs.getInt("${prefix}_ring", defaults.ring),
            notification = prefs.getInt("${prefix}_notification", defaults.notification),
            media = prefs.getInt("${prefix}_media", defaults.media),
            vibrate = prefs.getBoolean("${prefix}_vibrate", defaults.vibrate)
        ).normalized()
    }

    fun save(id: ProfileId, profile: SoundProfile) {
        val p = profile.normalized()
        val prefix = id.name.lowercase()
        // commit() позволяет сообщить об ошибке записи, а не обещать сохранение.
        check(prefs.edit()
            .putInt("${prefix}_ring", p.ring)
            .putInt("${prefix}_notification", p.notification)
            .putInt("${prefix}_media", p.media)
            .putBoolean("${prefix}_vibrate", p.vibrate)
            .commit()) { "Не удалось сохранить профиль" }
    }

    fun lastApplied(): ProfileId? = prefs.getString("last_applied", null)
        ?.let { saved -> ProfileId.entries.firstOrNull { it.name == saved } }

    fun markApplied(id: ProfileId) {
        prefs.edit().putString("last_applied", id.name).apply()
    }
}
