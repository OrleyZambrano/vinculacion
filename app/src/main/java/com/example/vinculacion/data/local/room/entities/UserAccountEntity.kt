package com.example.vinculacion.data.local.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.vinculacion.data.model.UserRole

@Entity(
    tableName = "user_accounts",
    indices = [
        Index(value = ["username_lower", "tag"], unique = true),
        Index(value = ["needs_sync"]),
        Index(value = ["requires_email"])
    ]
)
data class UserAccountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "username_lower") val usernameLower: String,
    val tag: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val email: String?,
    val role: UserRole,
    @ColumnInfo(name = "requires_email") val requiresEmail: Boolean,
    @ColumnInfo(name = "needs_sync") val needsSync: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
