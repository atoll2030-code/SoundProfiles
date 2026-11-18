package com.soundprofiles.app

enum class ProfileId(val label: String, val icon: String) {
    HOME("Дом", "⌂"),
    WORK("Работа", "▣"),
    STREET("Улица", "☀")
}

data class SoundProfile(
    val ring: Int,
    val notification: Int,
    val media: Int,
    val vibrate: Boolean
) {
    fun normalized(): SoundProfile = copy(
        ring = ring.coerceIn(0, 100),
        notification = notification.coerceIn(0, 100),
        media = media.coerceIn(0, 100)
    )
}

fun defaultProfile(id: ProfileId): SoundProfile = when (id) {
    ProfileId.HOME -> SoundProfile(70, 60, 50, false)
    ProfileId.WORK -> SoundProfile(0, 0, 20, true)
    ProfileId.STREET -> SoundProfile(100, 90, 80, false)
}
