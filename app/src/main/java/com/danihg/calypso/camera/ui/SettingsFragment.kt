package com.danihg.calypso.camera.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateViewModelFactory
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraSettingsViewModel
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.camera.models.SharedProfileViewModel
import com.danihg.calypso.camera.sources.CameraCalypsoSource
import com.google.android.material.button.MaterialButton

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private companion object {
        const val DEFAULT_PROGRESS = 5
    }

    // (1) Inicializamos CameraSettingsViewModel con SavedStateHandle
    private val settingsVm: CameraSettingsViewModel by viewModels {
        SavedStateViewModelFactory(requireActivity().application, this)
    }

    // (2) ViewModels adicionales
    private val sharedProfileVm: SharedProfileViewModel by activityViewModels()
    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    // (3) Referencias a vistas
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSettingsStream: MaterialButton
    private lateinit var btnSettingsCamera: MaterialButton

    private lateinit var btnCameraManual: MaterialButton
    private lateinit var btnCameraAuto: MaterialButton
    private lateinit var btnExposureCompensation: MaterialButton

    // → Nuestras 3 vistas nuevas:
    private lateinit var btnIso: MaterialButton
    private lateinit var btnExposureTime: MaterialButton
    private lateinit var btnWhiteBalance: MaterialButton

    private lateinit var tvProfileInfo: TextView
    private lateinit var tvEvValue: TextView
    private lateinit var seekBarExposure: SeekBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // —————————————————————————
        // 3.1) Bind de vistas
        // —————————————————————————
        btnSettings               = view.findViewById(R.id.btnSettings)
        btnSettingsStream         = view.findViewById(R.id.btnSettingsStream)
        btnSettingsCamera         = view.findViewById(R.id.btnSettingsCamera)
        btnCameraManual           = view.findViewById(R.id.btnCameraManual)
        btnCameraAuto             = view.findViewById(R.id.btnCameraAuto)
        btnExposureCompensation   = view.findViewById(R.id.btnExposureCompensation)

        // ——— NUESTROS 3 BOTONES NUEVOS ———
        btnIso                    = view.findViewById(R.id.btnIso)
        btnExposureTime           = view.findViewById(R.id.btnExposureTime)
        btnWhiteBalance           = view.findViewById(R.id.btnWhiteBalance)
        // —————————————————————————

        tvProfileInfo             = view.findViewById(R.id.tvProfileInfo)
        tvEvValue                 = view.findViewById(R.id.tvEvValue)
        seekBarExposure           = view.findViewById(R.id.seekBarExposure)

        // —————————————————————————
        // 3.2) Ajuste dinámico tamaño de tvProfileInfo
        // —————————————————————————
        val metrics = requireContext().resources.displayMetrics
        val anchoDp = metrics.widthPixels / metrics.density
        val sizeSp = if (anchoDp < 390f) 8f else 10f
        tvProfileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        // —————————————————————————
        // 3.3) Observamos SharedProfileViewModel para la etiqueta de perfil
        // —————————————————————————
        sharedProfileVm.loadedProfile.observe(viewLifecycleOwner) { updateProfileInfo() }
        sharedProfileVm.loadedProfileAlias.observe(viewLifecycleOwner) { updateProfileInfo() }

        // —————————————————————————
        // 3.4) Observadores de todos los LiveData en settingsVm
        // —————————————————————————

        settingsVm.isManualVisible.observe(viewLifecycleOwner) { visible ->
            btnCameraManual.visibility = if (visible) View.VISIBLE else View.GONE
        }

        settingsVm.isAutoVisible.observe(viewLifecycleOwner) { visible ->
            btnCameraAuto.visibility = if (visible) View.VISIBLE else View.GONE
        }

        settingsVm.isStreamCameraVisible.observe(viewLifecycleOwner) { visible ->
            val visibility = if (visible) View.VISIBLE else View.GONE
            btnSettingsStream.visibility = visibility
            btnSettingsCamera.visibility = visibility
        }

        settingsVm.isExposureButtonVisible.observe(viewLifecycleOwner) { visible ->
            btnExposureCompensation.visibility = if (visible) View.VISIBLE else View.GONE

            // Ajustar α según si el modo está activo o no
            settingsVm.isExposureModeActive.value?.let { active ->
                btnExposureCompensation.alpha = if (active) 0.4f else 0.8f
            }
        }

        settingsVm.isExposureModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                settingsVm.seekBarProgress.value?.let { progress ->
                    seekBarExposure.progress = progress
                    updateEvText(progress)
                    applyExposureCompensation(progress)
                }
            } else {
                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            }
            btnExposureCompensation.alpha = if (active) 0.4f else 0.8f
        }

        // Si el progreso almacenado no es el valor por defecto, se aplica automáticamente:
        settingsVm.seekBarProgress.value?.let { progress ->
            if (progress != DEFAULT_PROGRESS) {
                view.postDelayed({
                    applyExposureCompensation(progress)
                }, 300)
            }
        }

        // —————————————————————————
        // 3.5) Observamos nuestro nuevo LiveData: isManualOptionsVisible
        //        para alternar la visibilidad de btnIso, btnExposureTime, btnWhiteBalance
        // —————————————————————————
        settingsVm.isManualOptionsVisible.observe(viewLifecycleOwner) { visible ->
            val v = if (visible) View.VISIBLE else View.GONE
            btnIso.visibility = v
            btnExposureTime.visibility = v
            btnWhiteBalance.visibility = v
        }

        // —————————————————————————
        // 4) Click listener de btnSettings (botón principal)
        // —————————————————————————
        btnSettings.setOnClickListener {
            val anyVisible =
                (!btnCameraManual.isGone
                        || !btnCameraAuto.isGone
                        || !btnExposureCompensation.isGone
                        || !seekBarExposure.isGone
                        || !tvEvValue.isGone
                        || !btnSettingsStream.isGone
                        || !btnSettingsCamera.isGone
                        || !btnIso.isGone
                        || !btnExposureTime.isGone
                        || !btnWhiteBalance.isGone)

            if (anyVisible) {
                // 4.1) Ocultamos TODO y reseteamos flags en el ViewModel
                btnCameraManual.visibility        = View.GONE
                btnCameraAuto.visibility          = View.GONE
                btnExposureCompensation.visibility = View.GONE
                btnSettingsStream.visibility      = View.GONE
                btnSettingsCamera.visibility      = View.GONE
                seekBarExposure.visibility        = View.GONE
                tvEvValue.visibility              = View.GONE

                // → También ocultamos el grupo “Manual Options”
                btnIso.visibility                 = View.GONE
                btnExposureTime.visibility        = View.GONE
                btnWhiteBalance.visibility        = View.GONE

                // 4.2) Reiniciamos todos los estados en el ViewModel:
                settingsVm.setManualVisible(false)
                settingsVm.setAutoVisible(false)
                settingsVm.setExposureButtonVisible(false)
                settingsVm.setStreamCameraVisible(false)
                settingsVm.setExposureModeActive(false)
                settingsVm.setManualOptionsVisible(false)
            } else {
                // 4.3) Si no había nada visible, alternamos “Stream” y “Camera”
                val nextVis = if (btnSettingsStream.isGone) View.VISIBLE else View.GONE
                btnSettingsStream.visibility = nextVis
                btnSettingsCamera.visibility = nextVis
                settingsVm.setStreamCameraVisible(nextVis == View.VISIBLE)

                if (nextVis == View.VISIBLE) {
                    btnSettingsStream.animate().alpha(1f).setDuration(200).start()
                    btnSettingsCamera.animate().alpha(1f).setDuration(200).start()
                }
            }
        }

        // —————————————————————————
        // 5) Click listener de btnSettingsStream (igual que antes)
        // —————————————————————————
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

        // —————————————————————————
        // 6) Click listener de btnSettingsCamera:
        //    - oculto “Stream/Camera” y muestro “Manual” y “Auto”
        // —————————————————————————
        btnSettingsCamera.setOnClickListener {
            btnSettingsStream.visibility = View.GONE
            btnSettingsCamera.visibility = View.GONE

            btnCameraManual.visibility = View.VISIBLE
            btnCameraAuto.visibility   = View.VISIBLE

            btnCameraManual.alpha = 0f
            btnCameraAuto.alpha   = 0f
            btnCameraManual.animate().alpha(1f).setDuration(200).start()
            btnCameraAuto.animate().alpha(1f).setDuration(200).start()

            settingsVm.setManualVisible(true)
            settingsVm.setAutoVisible(true)
            settingsVm.setStreamCameraVisible(false)
        }

        // —————————————————————————
        // 7) Click listener de btnCameraManual:
        //    - ocultar Manual/Auto
        //    - mostrar ISO / ExposureTime / WhiteBalance
        // —————————————————————————
        btnCameraManual.setOnClickListener {
            // 7.1) Ocultamos Manual/Auto
            btnCameraManual.visibility = View.GONE
            btnCameraAuto.visibility   = View.GONE
            settingsVm.setManualVisible(false)
            settingsVm.setAutoVisible(false)

            // 7.2) Mostramos el grupo de “Manual Options”
            btnIso.alpha             = 0f
            btnExposureTime.alpha    = 0f
            btnWhiteBalance.alpha    = 0f
            btnIso.visibility        = View.VISIBLE
            btnExposureTime.visibility = View.VISIBLE
            btnWhiteBalance.visibility = View.VISIBLE

            btnIso.animate().alpha(1f).setDuration(200).start()
            btnExposureTime.animate().alpha(1f).setDuration(200).start()
            btnWhiteBalance.animate().alpha(1f).setDuration(200).start()

            settingsVm.setManualOptionsVisible(true)
        }

        // —————————————————————————
        // 8) Click listener de btnCameraAuto (igual que antes)
        // —————————————————————————
        btnCameraAuto.setOnClickListener {
            btnCameraManual.visibility = View.GONE
            btnCameraAuto.visibility   = View.GONE
            settingsVm.setManualVisible(false)
            settingsVm.setAutoVisible(false)

            val cameraSource = genericStream.videoSource
            if (cameraSource is CameraCalypsoSource) {
                cameraSource.enableAutoExposure()
                cameraSource.enableAutoISO()
                cameraSource.enableAutoFocus()
                cameraSource.enableAutoWhiteBalance(
                    CameraCharacteristics.CONTROL_AWB_MODE_AUTO
                )

                btnExposureCompensation.visibility = View.VISIBLE
                btnExposureCompensation.alpha = 0f
                btnExposureCompensation.animate().alpha(1f).setDuration(200).start()

                settingsVm.setExposureButtonVisible(true)
                settingsVm.setExposureModeActive(false)
            }
        }

        // —————————————————————————
        // 9) Click listener de btnExposureCompensation (igual que antes)
        // —————————————————————————
        btnExposureCompensation.setOnClickListener {
            val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return@setOnClickListener
            val currentlyActive = settingsVm.isExposureModeActive.value ?: false

            if (!currentlyActive) {
                settingsVm.setExposureModeActive(true)
                btnExposureCompensation.alpha = 0.4f

                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE

                val restored = settingsVm.seekBarProgress.value ?: DEFAULT_PROGRESS
                seekBarExposure.progress = restored
                updateEvText(restored)
            } else {
                settingsVm.setExposureModeActive(false)
                btnExposureCompensation.alpha = 0.8f

                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            }
        }

        // —————————————————————————
        // 10) ChangeListener del SeekBar (igual que antes)
        // —————————————————————————
        seekBarExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsVm.setSeekBarProgress(progress)
                    updateEvText(progress)
                    applyExposureCompensation(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { /*no-op*/ }
            override fun onStopTrackingTouch(sb: SeekBar) { /*no-op*/ }
        })

        // —————————————————————————
        // 11) Restaurar UI tras rotación o al volver de segundo plano
        // —————————————————————————
        restoreUIState()

        // —————————————————————————
        // 12) Siempre actualizamos la info de perfil
        // —————————————————————————
        updateProfileInfo()
    }

    override fun onResume() {
        super.onResume()
        // Al volver de segundo plano, restauramos estado
        restoreUIState()
    }

    /**
     * (A) Restaura todas las vistas según los LiveData en settingsVm
     */
    private fun restoreUIState() {
        // Manual / Auto
        val manualVisible = settingsVm.isManualVisible.value ?: false
        btnCameraManual.visibility = if (manualVisible) View.VISIBLE else View.GONE

        val autoVisible = settingsVm.isAutoVisible.value ?: false
        btnCameraAuto.visibility = if (autoVisible) View.VISIBLE else View.GONE

        // Stream / Camera
        val streamVisible = settingsVm.isStreamCameraVisible.value ?: false
        val streamVis = if (streamVisible) View.VISIBLE else View.GONE
        btnSettingsStream.visibility = streamVis
        btnSettingsCamera.visibility = streamVis

        // Exposure Compensation button
        val expButtonVisible = settingsVm.isExposureButtonVisible.value ?: false
        btnExposureCompensation.visibility = if (expButtonVisible) View.VISIBLE else View.GONE

        // Ajustar α del botón “Exposure Compensation”
        val expModeActive = settingsVm.isExposureModeActive.value ?: false
        btnExposureCompensation.alpha = if (expModeActive) 0.4f else 0.8f

        // Si “Exposure mode” está activo, mostrar SeekBar + etiqueta y aplicar
        if (expModeActive) {
            tvEvValue.visibility = View.VISIBLE
            seekBarExposure.visibility = View.VISIBLE
            val progress = settingsVm.seekBarProgress.value ?: DEFAULT_PROGRESS
            seekBarExposure.progress = progress
            updateEvText(progress)
            applyExposureCompensation(progress)
        } else {
            tvEvValue.visibility = View.GONE
            seekBarExposure.visibility = View.GONE
        }

        // —————————————————————————
        // Restaurar el nuevo grupo “Manual Options”
        // —————————————————————————
        val manualOptionsVisible = settingsVm.isManualOptionsVisible.value ?: false
        val mOptVis = if (manualOptionsVisible) View.VISIBLE else View.GONE
        btnIso.visibility = mOptVis
        btnExposureTime.visibility = mOptVis
        btnWhiteBalance.visibility = mOptVis
    }

    /**
     * (B) Aplica la compensación de exposición en la cámara
     */
    private fun applyExposureCompensation(progress: Int) {
        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return

        // Si la cámara aún no está lista, reintentar un poco más tarde
        if (!cameraSource.isRunning()) {
            view?.postDelayed({
                applyExposureCompensation(progress)
            }, 300)
            return
        }

        val multiplier = 4 // aquí defines cuánto amplificas cada paso de –5…+5
        val rawEv = progress - DEFAULT_PROGRESS
        var desiredIndex = rawEv * multiplier

        val minIndex = cameraSource.getMinExposureCompensation()
        val maxIndex = cameraSource.getMaxExposureCompensation()
        desiredIndex = desiredIndex.coerceIn(minIndex, maxIndex)

        cameraSource.setExposureCompensation(desiredIndex)
    }

    /**
     * (C) Actualiza la etiqueta EV en pantalla (muestra –5…+5)
     */
    private fun updateEvText(progress: Int) {
        val rawEv = progress - DEFAULT_PROGRESS
        tvEvValue.text = if (rawEv >= 0) "EV: +$rawEv" else "EV: $rawEv"
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
