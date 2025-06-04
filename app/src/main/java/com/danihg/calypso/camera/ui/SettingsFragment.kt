package com.danihg.calypso.camera.ui

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.camera.models.SharedProfileViewModel
import com.google.android.material.button.MaterialButton

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val sharedProfileVm: SharedProfileViewModel by activityViewModels()
    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSettingsStream: MaterialButton
    private lateinit var btnSettingsCamera: MaterialButton
    private lateinit var tvProfileInfo: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSettings       = view.findViewById(R.id.btnSettings)
        btnSettingsStream = view.findViewById(R.id.btnSettingsStream)
        btnSettingsCamera = view.findViewById(R.id.btnSettingsCamera)
        tvProfileInfo     = view.findViewById(R.id.tvProfileInfo)

        // 1. Calculamos ancho en dp
        val metrics = requireContext().resources.displayMetrics
        val anchoDp = metrics.widthPixels / metrics.density

        // 2. Si ancho < 390 dp, 8sp; en caso contrario, 10sp
        val sizeSp = if (anchoDp < 390f) 8f else 10f
        tvProfileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        // Observar cambios en el SharedProfileViewModel
        sharedProfileVm.loadedProfile.observe(viewLifecycleOwner) {
            updateProfileInfo()
        }
        sharedProfileVm.loadedProfileAlias.observe(viewLifecycleOwner) {
            updateProfileInfo()
        }

        btnSettings.setOnClickListener {
            val nextVisibility = if (btnSettingsStream.isGone) View.VISIBLE else View.GONE
            btnSettingsStream.visibility = nextVisibility
            btnSettingsCamera.visibility = nextVisibility

            if (nextVisibility == View.VISIBLE) {
                btnSettingsStream.animate().alpha(1f).setDuration(200).start()
                btnSettingsCamera.animate().alpha(1f).setDuration(200).start()
            }
        }

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
            // TODO: lógica para ajustes de cámara
        }

        // —————————————————————————
        // IMPORTANTE: Llamar a updateProfileInfo() aquí para que al cargar el fragment
        // se muestre inmediatamente el texto (ya sea el default o el guardado).
        updateProfileInfo()
    }

    private fun updateProfileInfo() {
        val prefs = requireActivity().getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)

        val alias = prefs.getString("last_loaded_profile", null)
        val resolution = prefs.getString("last_loaded_profile_resolution", null)
        val fps = prefs.getString("last_loaded_profile_fps", null)
        val rtmp = prefs.getString("last_loaded_profile_rtmp", null)

        if (!alias.isNullOrBlank() &&
            !resolution.isNullOrBlank() &&
            !fps.isNullOrBlank() &&
            !rtmp.isNullOrBlank()
        ) {
            tvProfileInfo.text = "$alias: $resolution @${fps}fps RTMP: $rtmp"
        } else {
            tvProfileInfo.text = "Default: 1080p @30fps RTMP: None"
        }

        tvProfileInfo.visibility = View.VISIBLE
    }
}
