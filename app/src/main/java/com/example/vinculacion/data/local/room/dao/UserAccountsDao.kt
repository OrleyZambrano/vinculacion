package com.example.vinculacion.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.vinculacion.data.local.room.entities.UserAccountEntity

@Dao
interface UserAccountsDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: UserAccountEntity)

    @Update
    suspend fun update(account: UserAccountEntity)

    @Query("SELECT * FROM user_accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE username_lower = :usernameLower LIMIT 10")
    suspend fun findByUsername(usernameLower: String): List<UserAccountEntity>

    @Query("SELECT * FROM user_accounts WHERE username_lower = :usernameLower AND tag = :tag LIMIT 1")
    suspend fun getByHandle(usernameLower: String, tag: String): UserAccountEntity?

    @Query("SELECT * FROM user_accounts WHERE needs_sync = 1")
    suspend fun getPendingSync(): List<UserAccountEntity>

    @Query("UPDATE user_accounts SET email = :email, requires_email = :requiresEmail, needs_sync = :needsSync, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateEmailState(id: String, email: String?, requiresEmail: Boolean, needsSync: Boolean, updatedAt: Long)

    @Query("UPDATE user_accounts SET role = :role, updated_at = :updatedAt, needs_sync = :needsSync WHERE id = :id")
    suspend fun updateRole(id: String, role: String, updatedAt: Long, needsSync: Boolean)

    @Query("UPDATE user_accounts SET needs_sync = :needsSync, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSyncState(id: String, needsSync: Boolean, updatedAt: Long)
}
