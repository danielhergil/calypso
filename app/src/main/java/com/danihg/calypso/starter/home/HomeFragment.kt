package com.danihg.calypso.starter.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.danihg.calypso.camera.CameraActivity
import com.danihg.calypso.databinding.FragmentHomeBinding
import com.danihg.calypso.settings.SettingsActivity
import com.danihg.calypso.teams.AddTeamsActivity

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
            val intent = Intent(requireContext(), CameraActivity::class.java)
            startActivity(intent)
        }
        binding.cardTeams.setOnClickListener {
            val intent = Intent(requireContext(), AddTeamsActivity::class.java)
            startActivity(intent)
        }
        binding.cardSettings?.setOnClickListener {
            // Arranca la actividad de Settings
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}