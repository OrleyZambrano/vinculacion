package com.example.vinculacion.data.model

/**
 * Expone el estado actual de autenticación almacenado localmente.
 */
data class AuthState(
    val profile: UserProfile,
    val isAuthenticated: Boolean,
    val lastSignInAt: Long?
)
