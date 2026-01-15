package com.example.vinculacion.ui.profile

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vinculacion.R
import com.example.vinculacion.data.model.UserRole
import com.example.vinculacion.databinding.FragmentProfileBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (_binding == null) return@registerForActivityResult
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            if (result.resultCode == Activity.RESULT_CANCELED) {
                showMessage(getString(R.string.profile_google_sign_in_cancelled))
            } else {
                showError(getString(R.string.profile_google_sign_in_error))
            }
            return@registerForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken ?: throw IllegalStateException("Token de Google inválido")
            viewModel.signInWithGoogle(idToken)
        } catch (error: Exception) {
            showError(error.localizedMessage ?: getString(R.string.profile_google_sign_in_error))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), options)

        binding.profileGoogleSignInButton.setOnClickListener { startGoogleSignIn() }
        binding.profileRefreshRoleButton.setOnClickListener { viewModel.refreshGuideRole() }
        binding.profileSignOutButton.setOnClickListener { 
            signOutFromGoogle()
            viewModel.signOut() 
        }
        
        collectState()
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

    private fun startGoogleSignIn() {
        if (!this::googleSignInClient.isInitialized) return
        // Fuerza la selección de cuenta cada vez
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun signOutFromGoogle() {
        if (!this::googleSignInClient.isInitialized) return
        googleSignInClient.signOut()
    }

    private fun renderState(state: ProfileUiState) {
        val auth = state.authState
        val profile = auth.profile
        val isAuthenticated = auth.isAuthenticated

        binding.profileGuestCard.isVisible = !isAuthenticated
        binding.profileContentCard.isVisible = isAuthenticated
        binding.profileRefreshRoleButton.isVisible = isAuthenticated
        binding.profileGoogleSignInButton.isVisible = !isAuthenticated
        binding.profileGoogleSignInButton.isEnabled = state.isConnected && !state.isProcessingAuth
        binding.profileGoogleSignInButton.text = getString(
            if (state.isProcessingAuth) R.string.profile_google_sign_in_loading else R.string.profile_google_sign_in_button
        )

        if (!isAuthenticated) {
            return
        }

        // Profile info for authenticated users
        binding.profileName.text = profile.displayName
        binding.profileRoleChip.text = roleLabel(profile.role)
        binding.profileEmailValue.text = profile.email ?: getString(R.string.profile_email_missing)

        // Action buttons
        binding.profileRefreshRoleButton.isEnabled = state.isConnected && !state.isProcessingAuth
        binding.profileRefreshRoleButton.text = getString(
            if (state.isProcessingAuth) R.string.profile_role_refresh_loading else R.string.profile_role_refresh_button
        )
        binding.profileSignOutButton.isVisible = isAuthenticated

        // Last sign in info
        binding.profileLastSignIn.isVisible = auth.lastSignInAt != null
        auth.lastSignInAt?.let { timestamp ->
            val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(timestamp)
            binding.profileLastSignIn.text = getString(R.string.profile_last_sign_in, formatted)
        }
    }

    private fun roleLabel(role: UserRole): String = when (role) {
        UserRole.GUIA -> getString(R.string.profile_role_guide_label)
        UserRole.USUARIO -> getString(R.string.profile_role_user_label)
        UserRole.INVITADO -> getString(R.string.profile_role_guest_label)
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        val color = ContextCompat.getColor(requireContext(), R.color.accent_color)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setBackgroundTint(color).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = ProfileFragment()
    }
}
