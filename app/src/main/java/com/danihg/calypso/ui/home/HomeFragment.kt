package com.danihg.calypso.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.danihg.calypso.R
import com.danihg.calypso.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentHomeBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardCamera.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_camera)
        }
        binding.cardTeams.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_teams)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}