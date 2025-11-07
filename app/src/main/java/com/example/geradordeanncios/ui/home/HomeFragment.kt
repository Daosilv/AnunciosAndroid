package com.example.geradordeanncios.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.geradordeanncios.R
import com.example.geradordeanncios.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.whatsappCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_whatsappFormFragment)
        }

        binding.telegramVideoCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_telegramVideoFragment)
        }

        binding.facebookCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_facebookFormFragment)
        }

        binding.instagramCard.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_instagramFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
