package com.danihg.calypso.camera.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.danihg.calypso.R
import com.google.android.material.button.MaterialButton

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // THIS must be `view.findViewById` — not activity.findViewById
        val btnSettings = view.findViewById<MaterialButton>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            // toggle your sub-buttons here…
        }
    }
}