package com.soundprofiles.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

sealed interface ApplyResult {
    data object PermissionNeeded : ApplyResult
    data class Applied(val warnings: List<String>) : ApplyResult
    data class Failed(val message: String) : ApplyResult
}

class SoundController(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun apply(profile: SoundProfile): ApplyResult {
        if (audio.isVolumeFixed) return ApplyResult.Failed("Устройство не разрешает менять громкость")
        val targetMode = if (profile.vibrate) AudioManager.RINGER_MODE_VIBRATE
            else AudioManager.RINGER_MODE_NORMAL

        // До изменения громкости убеждаемся, что возможное изменение DND разрешено.
        if (audio.ringerMode != targetMode && !notifications.isNotificationPolicyAccessGranted) {
            return ApplyResult.PermissionNeeded
        }

        return try {
            val warnings = mutableListOf<String>()
            setVolume(AudioManager.STREAM_MUSIC, profile.media)
            setVolume(AudioManager.STREAM_RING, profile.ring)
            setVolume(AudioManager.STREAM_NOTIFICATION, profile.notification)
            audio.ringerMode = targetMode

            // Некоторые прошивки объединяют STREAM_RING и STREAM_NOTIFICATION.
            if (!matches(AudioManager.STREAM_MUSIC, profile.media)) {
                warnings += "Громкость медиа ограничена системой"
            }
            if (!profile.vibrate && !matches(AudioManager.STREAM_RING, profile.ring)) {
                warnings += "Громкость звонка отличается от заданной"
            }
            if (!profile.vibrate && !matches(AudioManager.STREAM_NOTIFICATION, profile.notification)) {
                warnings += "Громкость уведомлений может быть связана со звонком"
            }
            if (audio.ringerMode != targetMode) {
                warnings += "Режим вибрации не переключился"
            }
            ApplyResult.Applied(warnings)
        } catch (_: SecurityException) {
            ApplyResult.PermissionNeeded
        } catch (e: RuntimeException) {
            ApplyResult.Failed(e.localizedMessage ?: "Система запретила изменение звука")
        }
    }

    private fun setVolume(stream: Int, percent: Int) {
        val min = audio.getStreamMinVolume(stream)
        val max = audio.getStreamMaxVolume(stream)
        val volume = min + ((max - min) * percent.coerceIn(0, 100) / 100f).roundToInt()
        audio.setStreamVolume(stream, volume.coerceIn(min, max), 0)
    }

    private fun matches(stream: Int, percent: Int): Boolean {
        val min = audio.getStreamMinVolume(stream)
        val max = audio.getStreamMaxVolume(stream)
        val expected = min + ((max - min) * percent.coerceIn(0, 100) / 100f).roundToInt()
        return audio.getStreamVolume(stream) == expected
    }
}
