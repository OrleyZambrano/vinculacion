package com.example.vinculacion.ui.routes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vinculacion.R
import com.example.vinculacion.data.model.GuideRoute
import com.example.vinculacion.databinding.FragmentMyRoutesBinding
import com.example.vinculacion.ui.common.UiState
import com.example.vinculacion.ui.guide.TourRouteEditorDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MyRoutesFragment : Fragment() {

    private var _binding: FragmentMyRoutesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MyRoutesViewModel by viewModels()

    private val adapter = MyRoutesAdapter(
        onEdit = { route -> openEdit(route) },
        onDelete = { route -> confirmDelete(route) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyRoutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        collectUiState()
        collectEvents()
    }

    private fun setupViews() {
        binding.myRoutesRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MyRoutesFragment.adapter
        }
        binding.myRoutesBack.setOnClickListener {
            val activity = activity
            if (activity is com.example.vinculacion.MainActivity) {
                activity.openMap()
            } else if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.myRoutesLoading.isVisible = state is UiState.Loading
                when (state) {
                    is UiState.Success -> {
                        binding.myRoutesEmpty.isVisible = state.data.isEmpty()
                        binding.myRoutesRecycler.isVisible = state.data.isNotEmpty()
                        adapter.submitList(state.data)
                    }
                    is UiState.Empty -> {
                        binding.myRoutesEmpty.isVisible = true
                        binding.myRoutesRecycler.isVisible = false
                        adapter.submitList(emptyList())
                    }
                    is UiState.Error -> {
                        binding.myRoutesEmpty.isVisible = true
                        binding.myRoutesRecycler.isVisible = false
                        adapter.submitList(emptyList())
                    }
                    UiState.Loading -> {
                        binding.myRoutesEmpty.isVisible = false
                        binding.myRoutesRecycler.isVisible = false
                    }
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is MyRoutesEvent.ShowMessage -> {
                        val message = when (event.message) {
                            MyRoutesMessage.DeleteSuccess -> getString(R.string.tour_route_delete_success)
                            MyRoutesMessage.DeleteError -> getString(R.string.tour_route_delete_error)
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun confirmDelete(route: GuideRoute) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.tour_route_delete_confirm))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteRoute(route)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openEdit(route: GuideRoute) {
        TourRouteEditorDialogFragment.newInstance(route)
            .show(childFragmentManager, "editRoute")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = MyRoutesFragment()
    }
}
