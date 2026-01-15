package com.example.vinculacion.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.vinculacion.databinding.FragmentInfoMessageBinding

class InfoMessageFragment : Fragment() {

    private var _binding: FragmentInfoMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInfoMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.infoTitle.text = requireArguments().getString(ARG_TITLE).orEmpty()
        binding.infoBody.text = requireArguments().getString(ARG_BODY).orEmpty()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_BODY = "arg_body"

        fun newInstance(title: String, body: String) = InfoMessageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_BODY, body)
            }
        }
    }
}
