package com.example.vinculacion.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.vinculacion.databinding.FragmentMapsBinding
import com.google.android.material.tabs.TabLayoutMediator

class MapsFragment : Fragment() {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private var tabMediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> TourRouteMapFragment()
                else -> HeatmapMapFragment()
            }
        }
        binding.mapsViewPager.adapter = adapter
        tabMediator = TabLayoutMediator(binding.mapsTabLayout, binding.mapsViewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Recorrido"
                else -> "Mapa de calor"
            }
        }
        tabMediator?.attach()
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        binding.mapsViewPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = MapsFragment()
    }
}
