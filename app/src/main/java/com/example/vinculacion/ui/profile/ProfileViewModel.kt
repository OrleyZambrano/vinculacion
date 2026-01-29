package com.example.vinculacion.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vinculacion.R
import com.example.vinculacion.core.network.NetworkMonitor
import com.example.vinculacion.data.model.AuthState
import com.example.vinculacion.data.model.UserProfile
import com.example.vinculacion.data.model.UserRole
import com.example.vinculacion.data.model.UserSettings
import com.example.vinculacion.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)
    private val networkMonitor = NetworkMonitor(application)
    private val authInProgress = MutableStateFlow(false)
    private val _settings = MutableStateFlow(UserSettings.defaults())
    val settings: StateFlow<UserSettings> = _settings

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    val state: StateFlow<ProfileUiState> = combine(
        authRepository.authState,
        networkMonitor.isConnected,
        authInProgress
    ) { auth, connected, processing ->
        ProfileUiState(auth, connected, processing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(AuthState(UserProfile.guest(), false, null), false, false)
    )

    init {
        viewModelScope.launch {
            authRepository.authState.collect { auth ->
                if (auth.isAuthenticated) {
                    refreshSettings()
                } else {
                    _settings.value = UserSettings.defaults()
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            authInProgress.value = true
            try {
                val profile = authRepository.signInWithGoogle(idToken)
                _events.send(ProfileEvent.Message(context.getString(R.string.profile_google_sign_in_success, profile.displayName)))
            } catch (error: Exception) {
                _events.send(ProfileEvent.Error(error.localizedMessage ?: context.getString(R.string.profile_google_sign_in_error)))
            } finally {
                authInProgress.value = false
            }
        }
    }

    fun signInWithFirebaseUser(firebaseUser: FirebaseUser) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            authInProgress.value = true
            try {
                val profile = authRepository.signInWithFirebaseUser(firebaseUser)
                _events.send(ProfileEvent.Message(context.getString(R.string.profile_google_sign_in_success, profile.displayName)))
            } catch (error: Exception) {
                _events.send(ProfileEvent.Error(error.localizedMessage ?: context.getString(R.string.profile_google_sign_in_error)))
            } finally {
                authInProgress.value = false
            }
        }
    }

    fun refreshGuideRole() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            authInProgress.value = true
            try {
                val updated = authRepository.refreshRoleFromCloud()
                if (updated != null) {
                    val label = context.roleLabel(updated.role)
                    val message = context.getString(R.string.profile_role_refresh_success, label)
                    _events.send(ProfileEvent.Message(message))
                } else {
                    _events.send(ProfileEvent.Error("No se pudo obtener la sesión actual"))
                }
            } catch (error: Exception) {
                _events.send(ProfileEvent.Error(error.localizedMessage ?: context.getString(R.string.profile_role_refresh_error)))
            } finally {
                authInProgress.value = false
            }
        }
    }

    fun updateProfile(displayName: String, phone: String?) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            authInProgress.value = true
            try {
                authRepository.updateProfileInfo(displayName, phone)
                _events.send(ProfileEvent.Message(context.getString(R.string.profile_edit_success)))
            } catch (error: Exception) {
                _events.send(ProfileEvent.Error(error.localizedMessage ?: context.getString(R.string.profile_edit_error)))
            } finally {
                authInProgress.value = false
            }
        }
    }

    fun updateNotifications(enabled: Boolean) {
        updateSettings(_settings.value.copy(notificationsEnabled = enabled))
    }

    fun updatePrivacy(publicProfile: Boolean) {
        updateSettings(_settings.value.copy(publicProfile = publicProfile))
    }

    private fun updateSettings(settings: UserSettings) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            authInProgress.value = true
            try {
                authRepository.updateUserSettings(settings)
                _settings.value = settings
                _events.send(ProfileEvent.Message(context.getString(R.string.profile_settings_save_success)))
            } catch (error: Exception) {
                _events.send(ProfileEvent.Error(error.localizedMessage ?: context.getString(R.string.profile_settings_error)))
            } finally {
                authInProgress.value = false
            }
        }
    }

    private fun refreshSettings() {
        viewModelScope.launch {
            runCatching {
                authRepository.getUserSettings()
            }.onSuccess { settings ->
                _settings.value = settings
            }.onFailure { error ->
                _events.send(ProfileEvent.Error(error.localizedMessage ?: getApplication<Application>().getString(R.string.profile_settings_error)))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            authRepository.signOut()
            _events.send(ProfileEvent.Message(context.getString(R.string.tour_sign_out_success)))
        }
    }

    init {
        // Initialization complete - only Google Sign-In used in production
    }

    private fun Application.roleLabel(role: UserRole): String = when (role) {
        UserRole.GUIA -> getString(R.string.profile_role_guide_label)
        UserRole.USUARIO -> getString(R.string.profile_role_user_label)
        UserRole.INVITADO -> getString(R.string.profile_role_guest_label)
    }
}

data class ProfileUiState(
    val authState: AuthState,
    val isConnected: Boolean,
    val isProcessingAuth: Boolean
)

sealed class ProfileEvent {
    data class Message(val text: String) : ProfileEvent()
    data class Error(val text: String) : ProfileEvent()
}
