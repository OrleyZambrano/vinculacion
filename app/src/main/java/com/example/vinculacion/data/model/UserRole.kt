package com.example.vinculacion.data.model

/**
 * Define los roles soportados por el módulo de tours.
 */
enum class UserRole {
    INVITADO,
    USUARIO,
    GUIA;

    fun isAuthenticated(): Boolean = this != INVITADO

    fun canManageTours(): Boolean = this == GUIA
}
