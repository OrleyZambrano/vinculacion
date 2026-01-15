package com.example.vinculacion.data.model

/**
 * Representa una cuenta de usuario almacenada localmente para sincronización diferida.
 */
data class UserAccount(
    val id: String,
    val firstName: String,
    val lastName: String,
    val username: String,
    val tag: String,
    val displayName: String,
    val email: String?,
    val role: UserRole,
    val requiresEmail: Boolean,
    val needsSync: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    val handle: String get() = "$username#$tag"
    val fullName: String get() = displayName
    val hasEmail: Boolean get() = !email.isNullOrBlank()
}
