package com.example.vinculacion.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Patterns
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.vinculacion.data.local.datastore.authDataStore
import com.example.vinculacion.data.local.room.VinculacionDatabase
import com.example.vinculacion.data.local.room.mappers.toDomain
import com.example.vinculacion.data.local.room.mappers.toEntity
import com.example.vinculacion.data.model.AuthState
import com.example.vinculacion.data.model.UserAccount
import com.example.vinculacion.data.model.UserProfile
import com.example.vinculacion.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Gestiona el estado de autenticación y el perfil almacenado localmente.
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.authDataStore
    private val database = VinculacionDatabase.getInstance(appContext)
    private val userAccountsDao = database.userAccountsDao()
    private val syncRepository = SyncRepository(appContext)
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val authState: Flow<AuthState> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.toAuthState() }

    suspend fun signInWithGoogle(idToken: String): UserProfile = withContext(Dispatchers.IO) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user ?: throw IllegalStateException("No se pudo obtener la cuenta de Google")
        val role = fetchAndEnsureRemoteRole(firebaseUser)
        val profile = firebaseUser.toProfile(role)
        persistProfile(profile)
        dataStore.edit { prefs ->
            prefs[KEY_LAST_SIGN_IN] = System.currentTimeMillis()
        }
        profile
    }

    suspend fun promoteToGuide(): UserProfile = withContext(Dispatchers.IO) {
        val updated = updateAccountIfExists { account ->
            if (account.role == UserRole.GUIA) account else account.copy(role = UserRole.GUIA, needsSync = true)
        }
        persistProfile(updated.toProfile())
        enqueueRoleChange(updated)
        updated.toProfile()
    }

    suspend fun downgradeToUser(): UserProfile = withContext(Dispatchers.IO) {
        val updated = updateAccountIfExists { account ->
            if (account.role == UserRole.USUARIO) account
            else account.copy(role = UserRole.USUARIO, needsSync = true)
        }
        persistProfile(updated.toProfile())
        enqueueRoleChange(updated)
        updated.toProfile()
    }

    suspend fun updateDisplayName(name: String) = withContext(Dispatchers.IO) {
        val sanitized = name.cleanName()
        if (sanitized.isBlank()) return@withContext
        val updated = updateAccountIfExists { account ->
            if (account.displayName == sanitized) account else account.copy(displayName = sanitized)
        }
        persistProfile(updated.toProfile())
    }

    suspend fun refreshRoleFromCloud(): UserProfile? = withContext(Dispatchers.IO) {
        val firebaseUser = firebaseAuth.currentUser ?: return@withContext null
        val current = authState.first().profile
        if (current.id != firebaseUser.uid) {
            return@withContext null
        }
        
        // Forzar obtención del rol actual desde Firebase
        val remoteRole = fetchRemoteRole(firebaseUser.uid)
        println("DEBUG: Rol actual local: ${current.role}, Rol remoto de Firebase: $remoteRole")
        
        // Siempre actualizar el perfil para asegurar sincronización
        val updated = current.copy(role = remoteRole, needsSync = false)
        persistProfile(updated)
        
        // Retornar el perfil actualizado siempre, incluso si el rol no cambió
        updated
    }

    suspend fun markAccountSynced(accountId: String) = withContext(Dispatchers.IO) {
        val entity = userAccountsDao.getById(accountId) ?: return@withContext
        if (!entity.needsSync) return@withContext
        val updatedAt = System.currentTimeMillis()
        userAccountsDao.updateSyncState(accountId, needsSync = false, updatedAt = updatedAt)
        val current = authState.first().profile
        if (current.id == accountId) {
            persistProfile(current.copy(needsSync = false))
        }
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    suspend fun currentProfile(): UserProfile = authState.first().profile

    private suspend fun insertAccount(account: UserAccount) {
        try {
            userAccountsDao.insert(account.toEntity())
        } catch (ex: SQLiteConstraintException) {
            throw IllegalArgumentException("El nombre de usuario ya existe, intenta con otro.", ex)
        }
    }

    private suspend fun updateAccountIfExists(transform: (UserAccount) -> UserAccount): UserAccount {
        val current = authState.first().profile
        if (current.isGuest) {
            throw IllegalStateException("No hay sesión activa para actualizar")
        }
        val entity = userAccountsDao.getById(current.id)
            ?: throw IllegalStateException("No se encontró la cuenta local para ${current.id}")
        val domain = entity.toDomain()
        val transformed = transform(domain)
        val updated = transformed.copy(
            createdAt = domain.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        userAccountsDao.update(updated.toEntity())
        return updated
    }

    private suspend fun generateUniqueTag(usernameLower: String): String {
        repeat(TAG_GENERATION_ATTEMPTS) {
            val candidate = Random.nextInt(0, TAG_MAX_VALUE).toString().padStart(TAG_LENGTH, '0')
            if (userAccountsDao.getByHandle(usernameLower, candidate) == null) {
                return candidate
            }
        }
        val fallback = abs(System.currentTimeMillis()).toString().takeLast(TAG_LENGTH).padStart(TAG_LENGTH, '0')
        return fallback
    }

    private suspend fun enqueueRegistration(account: UserAccount) {
        val payload = JSONObject().apply {
            put("id", account.id)
            put("firstName", account.firstName)
            put("lastName", account.lastName)
            put("username", account.username)
            put("tag", account.tag)
            put("displayName", account.displayName)
            put("createdAt", account.createdAt)
        }
        syncRepository.enqueue(SYNC_TYPE_USER_REGISTRATION, account.id, payload.toString())
    }

    private suspend fun enqueueEmailLink(account: UserAccount) {
        val payload = JSONObject().apply {
            put("id", account.id)
            put("email", account.email)
            put("updatedAt", account.updatedAt)
        }
        syncRepository.enqueue(SYNC_TYPE_USER_EMAIL_LINK, account.id, payload.toString())
    }

    private suspend fun fetchRemoteRole(userId: String): UserRole {
        return runCatching {
            println("DEBUG: Obteniendo rol desde Firebase para usuario: $userId")
            val snapshot = firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            if (!snapshot.exists()) {
                println("DEBUG: Documento de usuario no existe en Firebase")
                return@runCatching UserRole.USUARIO
            }
            
            val remoteRole = snapshot.getString(FIRESTORE_ROLE_FIELD)
            println("DEBUG: Rol obtenido de Firebase: '$remoteRole'")
            
            remoteRole?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.ROOT)
                ?.let { roleString ->
                    println("DEBUG: Intentando convertir rol: '$roleString'")
                    UserRole.valueOf(roleString)
                }
        }.getOrElse { error ->
            println("DEBUG: Error obteniendo rol: ${error.message}")
            UserRole.USUARIO
        } ?: UserRole.USUARIO
    }

    private suspend fun fetchAndEnsureRemoteRole(firebaseUser: FirebaseUser): UserRole {
        return runCatching {
            val userDoc = firestore.collection(FIRESTORE_USERS_COLLECTION)
                .document(firebaseUser.uid)
            val snapshot = userDoc.get().await()
            
            if (snapshot.exists()) {
                // El usuario ya existe, obtener su rol
                val remoteRole = snapshot.getString(FIRESTORE_ROLE_FIELD)
                remoteRole?.takeIf { it.isNotBlank() }
                    ?.uppercase(Locale.ROOT)
                    ?.let(UserRole::valueOf) ?: UserRole.USUARIO
            } else {
                // El usuario no existe, crearlo con rol USUARIO por defecto
                val defaultRole = UserRole.USUARIO
                val userData = mapOf(
                    FIRESTORE_ROLE_FIELD to defaultRole.name,
                    "email" to firebaseUser.email,
                    "displayName" to firebaseUser.displayName,
                    "createdAt" to System.currentTimeMillis()
                )
                userDoc.set(userData).await()
                defaultRole
            }
        }.getOrNull() ?: UserRole.USUARIO
    }

    private fun FirebaseUser.toProfile(role: UserRole): UserProfile {
        val fallbackName = email ?: "Sin nombre"
        val normalizedName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        val nameParts = displayName?.trim()?.split(" ", limit = 2)
            ?.map { part -> part.trim() }
            ?.filter { it.isNotEmpty() }
        val firstName = nameParts?.getOrNull(0)
        val lastName = nameParts?.getOrNull(1)
        return UserProfile(
            id = uid,
            displayName = normalizedName,
            email = email,
            phone = null, // Se puede configurar después
            role = role,
            username = email?.substringBefore("@"),
            tag = null,
            firstName = firstName,
            lastName = lastName,
            requiresEmail = email.isNullOrBlank(),
            needsSync = false
        )
    }

    private suspend fun enqueueRoleChange(account: UserAccount) {
        val payload = JSONObject().apply {
            put("id", account.id)
            put("role", account.role.name)
            put("updatedAt", account.updatedAt)
        }
        syncRepository.enqueue(SYNC_TYPE_USER_ROLE_CHANGE, account.id, payload.toString())
    }

    private suspend fun persistProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = profile.id
            prefs[KEY_USER_NAME] = profile.displayName
            profile.email?.let { prefs[KEY_USER_EMAIL] = it } ?: prefs.remove(KEY_USER_EMAIL)
            profile.phone?.let { prefs[KEY_USER_PHONE] = it } ?: prefs.remove(KEY_USER_PHONE)
            prefs[KEY_USER_ROLE] = profile.role.name.lowercase(Locale.ROOT)
            profile.username?.let { prefs[KEY_USER_USERNAME] = it } ?: prefs.remove(KEY_USER_USERNAME)
            profile.tag?.let { prefs[KEY_USER_TAG] = it } ?: prefs.remove(KEY_USER_TAG)
            profile.firstName?.let { prefs[KEY_USER_FIRST_NAME] = it } ?: prefs.remove(KEY_USER_FIRST_NAME)
            profile.lastName?.let { prefs[KEY_USER_LAST_NAME] = it } ?: prefs.remove(KEY_USER_LAST_NAME)
            prefs[KEY_USER_REQUIRES_EMAIL] = profile.requiresEmail
            prefs[KEY_USER_NEEDS_SYNC] = profile.needsSync
        }
    }

    private fun Preferences.toAuthState(): AuthState {
        val storedRole = this[KEY_USER_ROLE]?.uppercase(Locale.ROOT)
        val role = runCatching { storedRole?.let(UserRole::valueOf) }.getOrNull() ?: UserRole.INVITADO
        val profile = if (contains(KEY_USER_ID)) {
            UserProfile(
                id = this[KEY_USER_ID] ?: "guest",
                displayName = this[KEY_USER_NAME] ?: "Invitado",
                email = this[KEY_USER_EMAIL],
                phone = this[KEY_USER_PHONE],
                role = role,
                username = this[KEY_USER_USERNAME],
                tag = this[KEY_USER_TAG],
                firstName = this[KEY_USER_FIRST_NAME],
                lastName = this[KEY_USER_LAST_NAME],
                requiresEmail = this[KEY_USER_REQUIRES_EMAIL] ?: false,
                needsSync = this[KEY_USER_NEEDS_SYNC] ?: false
            )
        } else {
            UserProfile.guest()
        }
        val isAuthenticated = profile.role.isAuthenticated()
        return AuthState(
            profile = profile,
            isAuthenticated = isAuthenticated,
            lastSignInAt = this[KEY_LAST_SIGN_IN]
        )
    }

    private fun String.cleanName(): String = trim().replace(WHITESPACE_REGEX, " ")

    private fun String.cleanUsername(): String = trim()
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE_REGEX, "_")

    private fun validateRegistrationInput(first: String, last: String, username: String) {
        require(first.length >= MIN_NAME_LENGTH) { "El nombre debe tener al menos $MIN_NAME_LENGTH caracteres" }
        require(last.length >= MIN_NAME_LENGTH) { "El apellido debe tener al menos $MIN_NAME_LENGTH caracteres" }
        require(USERNAME_REGEX.matches(username)) {
            "El nombre de usuario solo puede incluir letras, números, puntos, guiones y guiones bajos (3 a 20 caracteres)"
        }
    }

    private fun isValidEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun UserAccount.toProfile(): UserProfile = UserProfile(
        id = id,
        displayName = displayName,
        email = email,
        phone = null, // UserAccount doesn't have phone, using null
        role = role,
        username = username,
        tag = tag,
        firstName = firstName,
        lastName = lastName,
        requiresEmail = requiresEmail,
        needsSync = needsSync
    )

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("auth_user_id")
        private val KEY_USER_NAME = stringPreferencesKey("auth_user_name")
        private val KEY_USER_EMAIL = stringPreferencesKey("auth_user_email")
        private val KEY_USER_PHONE = stringPreferencesKey("auth_user_phone")
        private val KEY_USER_ROLE = stringPreferencesKey("auth_user_role")
        private val KEY_USER_USERNAME = stringPreferencesKey("auth_user_username")
        private val KEY_USER_TAG = stringPreferencesKey("auth_user_tag")
        private val KEY_USER_FIRST_NAME = stringPreferencesKey("auth_user_first_name")
        private val KEY_USER_LAST_NAME = stringPreferencesKey("auth_user_last_name")
        private val KEY_USER_REQUIRES_EMAIL = booleanPreferencesKey("auth_user_requires_email")
        private val KEY_USER_NEEDS_SYNC = booleanPreferencesKey("auth_user_needs_sync")
        private val KEY_LAST_SIGN_IN = longPreferencesKey("auth_last_sign_in")

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val USERNAME_REGEX = Regex("^[a-z0-9._-]{3,20}$")
        private const val MIN_NAME_LENGTH = 2
        private const val TAG_LENGTH = 4
        private const val TAG_MAX_VALUE = 10000
        private const val TAG_GENERATION_ATTEMPTS = 20

        private const val SYNC_TYPE_USER_REGISTRATION = "user_registration"
        private const val SYNC_TYPE_USER_EMAIL_LINK = "user_email_link"
        private const val SYNC_TYPE_USER_ROLE_CHANGE = "user_role_change"

        private const val FIRESTORE_USERS_COLLECTION = "users"
        private const val FIRESTORE_ROLE_FIELD = "role"
    }
}
