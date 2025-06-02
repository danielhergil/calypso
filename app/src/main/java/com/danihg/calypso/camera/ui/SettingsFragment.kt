package com.danihg.calypso.camera.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.CameraViewModel
import com.google.android.material.button.MaterialButton

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSettingsStream: MaterialButton
    private lateinit var btnSettingsCamera: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSettings       = view.findViewById(R.id.btnSettings)
        btnSettingsStream = view.findViewById(R.id.btnSettingsStream)
        btnSettingsCamera = view.findViewById(R.id.btnSettingsCamera)

        btnSettings.setOnClickListener {
            // Alterna la visibilidad de los dos botones adicionales
            val nextVisibility = if (btnSettingsStream.isGone) View.VISIBLE else View.GONE
            btnSettingsStream.visibility = nextVisibility
            btnSettingsCamera.visibility = nextVisibility

            // (Opcional) Animación sencilla
            if (nextVisibility == View.VISIBLE) {
                btnSettingsStream.animate().alpha(1f).setDuration(200).start()
                btnSettingsCamera.animate().alpha(1f).setDuration(200).start()
            }
        }

        // Listeners para los botones adicionales
        btnSettingsStream.setOnClickListener {
            if (genericStream.isStreaming || genericStream.isRecording) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, ActiveStreamSettingsFragment())
                    .addToBackStack(null)
                    .commit()
            } else {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, StreamSettingsFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
        btnSettingsCamera.setOnClickListener {
            // TODO: tu lógica para ajustes de cámara
        }
    }
}