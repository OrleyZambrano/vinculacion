package com.example.vinculacion.data.model

/**
 * Preferencias de cuenta sincronizadas con Firebase.
 */
data class UserSettings(
    val notificationsEnabled: Boolean,
    val publicProfile: Boolean
) {
    companion object {
        fun defaults(): UserSettings = UserSettings(
            notificationsEnabled = true,
            publicProfile = true
        )
    }
}
