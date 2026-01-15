package com.example.vinculacion.data.local.room.mappers

import com.example.vinculacion.data.local.room.entities.UserAccountEntity
import com.example.vinculacion.data.model.UserAccount
import java.util.Locale

fun UserAccountEntity.toDomain(): UserAccount = UserAccount(
    id = id,
    firstName = firstName,
    lastName = lastName,
    username = username,
    tag = tag,
    displayName = displayName,
    email = email,
    role = role,
    requiresEmail = requiresEmail,
    needsSync = needsSync,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun UserAccount.toEntity(): UserAccountEntity = UserAccountEntity(
    id = id,
    firstName = firstName,
    lastName = lastName,
    username = username,
    usernameLower = username.lowercase(Locale.ROOT),
    tag = tag,
    displayName = displayName,
    email = email,
    role = role,
    requiresEmail = requiresEmail,
    needsSync = needsSync,
    createdAt = createdAt,
    updatedAt = updatedAt
)
