package com.example.vinculacion.data.model

import java.util.UUID

/**
 * Representa el perfil básico almacenado en el dispositivo.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String?,
    val phone: String?, // WhatsApp del usuario
    val role: UserRole,
    val username: String? = null,
    val tag: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val requiresEmail: Boolean = false,
    val needsSync: Boolean = false
) {
    val isGuest: Boolean get() = role == UserRole.INVITADO
    val handle: String? get() = if (!username.isNullOrBlank() && !tag.isNullOrBlank()) "$username#$tag" else null

    companion object {
        fun guest(): UserProfile = UserProfile(
            id = "guest",
            displayName = "Invitado",
            email = null,
            phone = null,
            role = UserRole.INVITADO,
            username = null,
            tag = null,
            firstName = null,
            lastName = null,
            requiresEmail = false,
            needsSync = false
        )

        fun create(
            name: String,
            email: String?,
            phone: String? = null,
            role: UserRole,
            username: String? = null,
            tag: String? = null,
            firstName: String? = null,
            lastName: String? = null,
            requiresEmail: Boolean = false,
            needsSync: Boolean = false
        ): UserProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            displayName = name.ifBlank { "Sin nombre" },
            email = email?.ifBlank { null },
            phone = phone?.ifBlank { null },
            role = role,
            username = username,
            tag = tag,
            firstName = firstName,
            lastName = lastName,
            requiresEmail = requiresEmail,
            needsSync = needsSync
        )
    }
}
