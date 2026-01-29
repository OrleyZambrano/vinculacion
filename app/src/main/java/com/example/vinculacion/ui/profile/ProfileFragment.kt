package com.example.vinculacion.ui.profile

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.data.model.UserRole
import com.example.vinculacion.data.model.UserSettings
import com.example.vinculacion.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.DateFormat
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private var currentSettings: UserSettings = UserSettings.defaults()
    private var roleRefreshRequested = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.profileGoogleSignInButton.setOnClickListener { showGoogleSignInDialog() }
        binding.profileSignOutButton.setOnClickListener { showGoogleSignOutDialog() }
        binding.profileEditButton.setOnClickListener { showEditProfileDialog() }
        binding.profileNotificationsButton.setOnClickListener { showNotificationsDialog() }
        binding.profilePrivacyButton.setOnClickListener { showPrivacyDialog() }
        binding.profileAboutButton.setOnClickListener { showMessage(getString(R.string.profile_option_about_message)) }
        
        collectState()
        collectSettings()
        collectEvents()
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ProfileEvent.Message -> showMessage(event.text)
                        is ProfileEvent.Error -> showError(event.text)
                    }
                }
            }
        }
    }

    private fun collectSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    currentSettings = settings
                }
            }
        }
    }

    private fun startGoogleSignIn() {
        val provider = OAuthProvider.newBuilder("google.com")
            .addCustomParameter("prompt", "select_account")
            .setScopes(listOf("email", "profile"))
            .build()

        val pending = firebaseAuth.pendingAuthResult
        if (pending != null) {
            pending
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        viewModel.signInWithFirebaseUser(user)
                    } else {
                        showError(getString(R.string.profile_google_sign_in_error))
                    }
                }
                .addOnFailureListener { error ->
                    showError(error.localizedMessage ?: getString(R.string.profile_google_sign_in_error))
                }
            return
        }

        firebaseAuth.startActivityForSignInWithProvider(requireActivity(), provider)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    viewModel.signInWithFirebaseUser(user)
                } else {
                    showError(getString(R.string.profile_google_sign_in_error))
                }
            }
            .addOnFailureListener { error ->
                showError(error.localizedMessage ?: getString(R.string.profile_google_sign_in_error))
            }
    }

    private fun showGoogleSignInDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile_google_sign_in_title))
            .setMessage(getString(R.string.profile_google_sign_in_message))
            .setPositiveButton(R.string.profile_google_sign_in_action) { _, _ ->
                startGoogleSignIn()
            }
            .setNegativeButton(android.R.string.cancel, null)
        showSizedDialog(builder)
    }

    private fun showGoogleSignOutDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile_google_sign_out_title))
            .setMessage(getString(R.string.profile_google_sign_out_message))
            .setPositiveButton(R.string.profile_google_sign_out_action) { _, _ ->
                viewModel.signOut()
            }
            .setNegativeButton(android.R.string.cancel, null)
        showSizedDialog(builder)
    }

    private fun renderState(state: ProfileUiState) {
        val auth = state.authState
        val profile = auth.profile
        val isAuthenticated = auth.isAuthenticated

        binding.profileGuestCard.isVisible = !isAuthenticated
        binding.profileContentCard.isVisible = isAuthenticated
        binding.profileGoogleSignInButton.isVisible = !isAuthenticated
        binding.profileGoogleSignInButton.isEnabled = state.isConnected && !state.isProcessingAuth
        binding.profileGoogleSignInButton.text = getString(
            if (state.isProcessingAuth) R.string.profile_google_sign_in_loading else R.string.profile_google_sign_in_button
        )

        if (!isAuthenticated) {
            roleRefreshRequested = false
            return
        }

        if (!roleRefreshRequested) {
            roleRefreshRequested = true
            viewModel.refreshGuideRole()
        }

        // Profile info for authenticated users
        binding.profileName.text = profile.displayName
        binding.profileRoleChip.text = roleLabel(profile.role)
        binding.profileEmailValue.text = profile.email ?: getString(R.string.profile_email_missing)

        binding.profileOptionsContainer.isVisible = isAuthenticated
        binding.profileOptionsTitle.isVisible = isAuthenticated
        binding.profileSignOutButton.isVisible = isAuthenticated

        // Last sign in info
        binding.profileLastSignIn.isVisible = auth.lastSignInAt != null
        auth.lastSignInAt?.let { timestamp ->
            val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(timestamp)
            binding.profileLastSignIn.text = getString(R.string.profile_last_sign_in, formatted)
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val inputName = dialogView.findViewById<TextInputEditText>(R.id.profileEditNameInput)
        val inputPhone = dialogView.findViewById<TextInputEditText>(R.id.profileEditPhoneInput)

        val profile = viewModel.state.value.authState.profile
        inputName.setText(profile.displayName)
        inputPhone.setText(profile.phone.orEmpty())

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile_edit_title))
            .setView(dialogView)
            .setPositiveButton(R.string.profile_edit_save) { _, _ ->
                val name = inputName.text?.toString().orEmpty()
                val phone = inputPhone.text?.toString()
                viewModel.updateProfile(name, phone)
            }
            .setNegativeButton(R.string.profile_edit_cancel, null)
        showSizedDialog(builder)
    }

    private fun showNotificationsDialog() {
        showSettingsDialog(
            title = getString(R.string.profile_notifications_title),
            description = getString(R.string.profile_notifications_description),
            initialValue = currentSettings.notificationsEnabled,
            onSave = { enabled -> viewModel.updateNotifications(enabled) }
        )
    }

    private fun showPrivacyDialog() {
        showSettingsDialog(
            title = getString(R.string.profile_privacy_title),
            description = getString(R.string.profile_privacy_description),
            initialValue = currentSettings.publicProfile,
            onSave = { enabled -> viewModel.updatePrivacy(enabled) }
        )
    }

    private fun showSettingsDialog(
        title: String,
        description: String,
        initialValue: Boolean,
        onSave: (Boolean) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_setting, null)
        val descriptionView = dialogView.findViewById<android.widget.TextView>(R.id.profileSettingDescription)
        val switchView = dialogView.findViewById<SwitchMaterial>(R.id.profileSettingSwitch)

        descriptionView.text = description
        switchView.isChecked = initialValue

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.profile_edit_save) { _, _ ->
                onSave(switchView.isChecked)
            }
            .setNegativeButton(R.string.profile_edit_cancel, null)
        showSizedDialog(builder)
    }

    private fun showSizedDialog(builder: MaterialAlertDialogBuilder) {
        val dialog = builder.create()
        dialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun roleLabel(role: UserRole): String = when (role) {
        UserRole.GUIA -> getString(R.string.profile_role_guide_label)
        UserRole.USUARIO -> getString(R.string.profile_role_user_label)
        UserRole.INVITADO -> getString(R.string.profile_role_guest_label)
    }

    private fun showMessage(message: String) {
        val toastView = layoutInflater.inflate(R.layout.toast_success, null)
        val textView = toastView.findViewById<TextView>(R.id.toastText)
        textView.text = message
        Toast(requireContext()).apply {
            duration = Toast.LENGTH_SHORT
            @Suppress("DEPRECATION")
            view = toastView
        }.show()
    }

    private fun showError(message: String) {
        val color = ContextCompat.getColor(requireContext(), R.color.accent_color)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setBackgroundTint(color).show()
    }

    override fun onDestroyView() {
        _binding = null
        roleRefreshRequested = false
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = ProfileFragment()
    }
}
