package com.danihg.calypso.camera.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
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
        const val DEFAULT_PROGRESS = 5   // Para EV (–5..+5)
    }

    // 1) ViewModel con SavedStateHandle
    private val settingsVm: CameraSettingsViewModel by viewModels {
        SavedStateViewModelFactory(requireActivity().application, this)
    }

    // 2) Otros ViewModels
    private val sharedProfileVm: SharedProfileViewModel by activityViewModels()
    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    // 3) Referencias a vistas
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSettingsStream: MaterialButton
    private lateinit var btnSettingsCamera: MaterialButton

    private lateinit var btnCameraManual: MaterialButton
    private lateinit var btnCameraAuto: MaterialButton
    private lateinit var btnExposureCompensation: MaterialButton

    // — Nuevos botones de “Manual Mode”:
    private lateinit var btnIso: MaterialButton
    private lateinit var btnExposureTime: MaterialButton
    private lateinit var btnWhiteBalance: MaterialButton

    private lateinit var tvProfileInfo: TextView
    private lateinit var tvEvValue: TextView      // Para la etiqueta del slider activo
    private lateinit var seekBarExposure: SeekBar  // Un único SeekBar que se reconfigura

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

        btnIso                    = view.findViewById(R.id.btnIso)
        btnExposureTime           = view.findViewById(R.id.btnExposureTime)
        btnWhiteBalance           = view.findViewById(R.id.btnWhiteBalance)

        tvProfileInfo             = view.findViewById(R.id.tvProfileInfo)
        tvEvValue                 = view.findViewById(R.id.tvEvValue)
        seekBarExposure           = view.findViewById(R.id.seekBarExposure)

        // —————————————————————————
        // 3.2) Ajuste dinámico tamaño tvProfileInfo
        // —————————————————————————
        val metrics = requireContext().resources.displayMetrics
        val anchoDp = metrics.widthPixels / metrics.density
        val sizeSp = if (anchoDp < 390f) 8f else 10f
        tvProfileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        // —————————————————————————
        // 3.3) Observamos SharedProfileViewModel para texto de perfil
        // —————————————————————————
        sharedProfileVm.loadedProfile.observe(viewLifecycleOwner) { updateProfileInfo() }
        sharedProfileVm.loadedProfileAlias.observe(viewLifecycleOwner) { updateProfileInfo() }

        // —————————————————————————
        // 3.4) Observadores LiveData en settingsVm
        // —————————————————————————

        // (a) Mostrar/ocultar “Manual” / “Auto”
        settingsVm.isManualVisible.observe(viewLifecycleOwner) { v ->
            btnCameraManual.visibility = if (v) View.VISIBLE else View.GONE
        }
        settingsVm.isAutoVisible.observe(viewLifecycleOwner) { v ->
            btnCameraAuto.visibility = if (v) View.VISIBLE else View.GONE
        }

        // (b) Mostrar/ocultar “Stream” / “Camera”
        settingsVm.isStreamCameraVisible.observe(viewLifecycleOwner) { v ->
            val visibility = if (v) View.VISIBLE else View.GONE
            btnSettingsStream.visibility = visibility
            btnSettingsCamera.visibility = visibility
        }

        // (c) Mostrar/ocultar botón “Exposure Compensation”
        settingsVm.isExposureButtonVisible.observe(viewLifecycleOwner) { v ->
            btnExposureCompensation.visibility = if (v) View.VISIBLE else View.GONE
            settingsVm.isExposureModeActive.value?.let { active ->
                btnExposureCompensation.alpha = if (active) 0.4f else 0.8f
            }
        }

        // (d) Modo “Exposure Compensation” activo/inactivo
        settingsVm.isExposureModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreExposureSlider()
            } else {
                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            }
            btnExposureCompensation.alpha = if (active) 0.4f else 0.8f
        }

        // Si EV guardado ≠ DEFAULT, aplicamos al inicio
        settingsVm.seekBarProgress.value?.let { p ->
            if (p != DEFAULT_PROGRESS) {
                view.postDelayed({ applyExposureCompensation(p) }, 300)
            }
        }

        // (e) Modo “ISO” (solo mostrar slider cuando esté activo)
        settingsVm.isISOModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreISOSlider()
            } else {
                // si no hay otros modos, se oculta
                if (settingsVm.isExposureModeActive.value == false
                    && settingsVm.isWBModeActive.value == false
                    && settingsVm.isETModeActive.value == false) {
                    tvEvValue.visibility = View.GONE
                    seekBarExposure.visibility = View.GONE
                }
            }
        }

        // (f) Modo “White Balance”
        settingsVm.isWBModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreWbSlider()
            } else {
                if (settingsVm.isExposureModeActive.value == false
                    && settingsVm.isISOModeActive.value == false
                    && settingsVm.isETModeActive.value == false) {
                    tvEvValue.visibility = View.GONE
                    seekBarExposure.visibility = View.GONE
                }
            }
        }

        // (g) Modo “Exposure Time”
        settingsVm.isETModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreEtSlider()
            } else {
                if (settingsVm.isExposureModeActive.value == false
                    && settingsVm.isISOModeActive.value == false
                    && settingsVm.isWBModeActive.value == false) {
                    tvEvValue.visibility = View.GONE
                    seekBarExposure.visibility = View.GONE
                }
            }
        }

        // (h) Mostrar/ocultar botones “ISO/ET/WB” (grupo “Manual Options”)
        settingsVm.isManualOptionsVisible.observe(viewLifecycleOwner) { v ->
            val vis = if (v) View.VISIBLE else View.GONE
            btnIso.visibility = vis
            btnExposureTime.visibility = vis
            btnWhiteBalance.visibility = vis
        }

        // —————————————————————————
        // 4) Click listener “Settings” principal
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
                // Ocultamos TODO y reiniciamos flags
                btnCameraManual.visibility        = View.GONE
                btnCameraAuto.visibility          = View.GONE
                btnExposureCompensation.visibility = View.GONE
                btnSettingsStream.visibility      = View.GONE
                btnSettingsCamera.visibility      = View.GONE
                seekBarExposure.visibility        = View.GONE
                tvEvValue.visibility              = View.GONE

                btnIso.visibility                 = View.GONE
                btnExposureTime.visibility        = View.GONE
                btnWhiteBalance.visibility        = View.GONE

                settingsVm.setManualVisible(false)
                settingsVm.setAutoVisible(false)
                settingsVm.setExposureButtonVisible(false)
                settingsVm.setStreamCameraVisible(false)

                // Apagamos todos los modos manuales
                settingsVm.setExposureModeActive(false)
                settingsVm.setIsISOModeActive(false)
                settingsVm.setIsWBModeActive(false)
                settingsVm.setIsETModeActive(false)
                settingsVm.setManualOptionsVisible(false)

                // Apagamos “modo manual global” (para el caso ISO)
                settingsVm.setManualMode(false)
            } else {
                // Alternamos “Stream / Camera”
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
        // 5) Click listener “Settings Stream”
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
        // 6) Click listener “Settings Camera”
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
        // 7) Click listener “Camera Manual”
        // —————————————————————————
        btnCameraManual.setOnClickListener {
            // Entramos en “modo manual global”
            settingsVm.setManualMode(true)

            // Ocultamos “Manual” / “Auto”
            btnCameraManual.visibility = View.GONE
            btnCameraAuto.visibility   = View.GONE
            settingsVm.setManualVisible(false)
            settingsVm.setAutoVisible(false)

            // Mostramos los 3 botones “ISO / ET / WB”
            btnIso.alpha = 0f
            btnExposureTime.alpha = 0f
            btnWhiteBalance.alpha = 0f

            btnIso.visibility         = View.VISIBLE
            btnExposureTime.visibility = View.VISIBLE
            btnWhiteBalance.visibility = View.VISIBLE

            btnIso.animate().alpha(1f).setDuration(200).start()
            btnExposureTime.animate().alpha(1f).setDuration(200).start()
            btnWhiteBalance.animate().alpha(1f).setDuration(200).start()

            settingsVm.setManualOptionsVisible(true)
        }

        // —————————————————————————
        // 8) Click listener “Camera Auto”
        // —————————————————————————
        btnCameraAuto.setOnClickListener {
            // Salimos de “modo manual global”
            settingsVm.setManualMode(false)

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
        // 9) Click listener “Exposure Compensation”
        // —————————————————————————
        btnExposureCompensation.setOnClickListener {
            val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return@setOnClickListener
            val currentlyActive = settingsVm.isExposureModeActive.value ?: false

            if (!currentlyActive) {
                // Entramos modo “Exposure Compensation”
                settingsVm.setExposureModeActive(true)
                settingsVm.setIsISOModeActive(false)
                settingsVm.setIsWBModeActive(false)
                settingsVm.setIsETModeActive(false)

                btnExposureCompensation.alpha = 0.4f
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE

                // Restaurar progreso EV
                val restored = settingsVm.seekBarProgress.value ?: DEFAULT_PROGRESS
                seekBarExposure.max = 10
                seekBarExposure.progress = restored
                updateEvText(restored)
            } else {
                // Salimos de modo
                settingsVm.setExposureModeActive(false)
                btnExposureCompensation.alpha = 0.8f

                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            }
        }

        // —————————————————————————
        // 10) CHANGE LISTENER SeekBar
        // —————————————————————————
        seekBarExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cameraSource = genericStream.videoSource as? CameraCalypsoSource ?: return

                when {
                    // 1) MODO “Exposure Compensation”
                    settingsVm.isExposureModeActive.value == true -> {
                        settingsVm.setSeekBarProgress(progress)
                        updateEvText(progress)
                        applyExposureCompensation(progress)
                    }
                    // 2) MODO “ISO”
                    settingsVm.isISOModeActive.value == true -> {
                        settingsVm.setIsoSeekProgress(progress)
                        applyISOSlider(progress, cameraSource)
                    }
                    // 3) MODO “WHITE BALANCE”
                    settingsVm.isWBModeActive.value == true -> {
                        settingsVm.setWbSeekProgress(progress)
                        applyWbSlider(progress, cameraSource)
                    }
                    // 4) MODO “EXPOSURE TIME”
                    settingsVm.isETModeActive.value == true -> {
                        settingsVm.setEtSeekProgress(progress)
                        applyEtSlider(progress, cameraSource)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { /*no-op*/ }
            override fun onStopTrackingTouch(sb: SeekBar) { /*no-op*/ }
        })

        // —————————————————————————
        // 11) Click listener “ISO”
        // —————————————————————————
        btnIso.setOnClickListener {
            // Desactivar todos los modos manuales previos
            settingsVm.setExposureModeActive(false)
            settingsVm.setIsWBModeActive(false)
            settingsVm.setIsETModeActive(false)

            // Toggle ISO mode
            val isoActive = settingsVm.isISOModeActive.value ?: false
            if (isoActive) {
                // Ya estaba en ISO → lo apagamos
                settingsVm.setIsISOModeActive(false)
                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            } else {
                // Entramos en ISO
                settingsVm.setIsISOModeActive(true)
                settingsVm.setIsWBModeActive(false)
                settingsVm.setIsETModeActive(false)
                settingsVm.setExposureModeActive(false)

                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreISOSlider()
            }
        }

        // —————————————————————————
        // 12) Click listener “White Balance”
        // —————————————————————————
        btnWhiteBalance.setOnClickListener {
            // Desactivar todos los modos manuales previos
            settingsVm.setExposureModeActive(false)
            settingsVm.setIsISOModeActive(false)
            settingsVm.setIsETModeActive(false)

            // Toggle WB mode
            val wbActive = settingsVm.isWBModeActive.value ?: false
            if (wbActive) {
                settingsVm.setIsWBModeActive(false)
                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            } else {
                settingsVm.setIsWBModeActive(true)
                settingsVm.setExposureModeActive(false)
                settingsVm.setIsISOModeActive(false)
                settingsVm.setIsETModeActive(false)

                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreWbSlider()
            }
        }

        // —————————————————————————
        // 13) Click listener “Exposure Time”
        // —————————————————————————
        btnExposureTime.setOnClickListener {
            // Desactivar todos los modos manuales previos
            settingsVm.setExposureModeActive(false)
            settingsVm.setIsISOModeActive(false)
            settingsVm.setIsWBModeActive(false)

            // Toggle ET mode
            val etActive = settingsVm.isETModeActive.value ?: false
            if (etActive) {
                settingsVm.setIsETModeActive(false)
                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            } else {
                settingsVm.setIsETModeActive(true)
                settingsVm.setExposureModeActive(false)
                settingsVm.setIsISOModeActive(false)
                settingsVm.setIsWBModeActive(false)

                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreEtSlider()
            }
        }

        // —————————————————————————
        // 14) Restaurar UIState tras rotación / volver de fondo
        // —————————————————————————
        restoreUIState()

        // —————————————————————————
        // 15) Info de perfil
        // —————————————————————————
        updateProfileInfo()
    }

    override fun onResume() {
        super.onResume()
        restoreUIState()
    }

    // ------------------------------------------------------
    // (A) Métodos auxiliares para “Recovery” de cada slider
    // ------------------------------------------------------

    /** Restaura el SeekBar en modo “EV” y actualiza etiqueta. */
    private fun restoreExposureSlider() {
        val progress = settingsVm.seekBarProgress.value ?: DEFAULT_PROGRESS
        seekBarExposure.max = 10
        seekBarExposure.progress = progress
        updateEvText(progress)
    }

    /** Restaura el SeekBar en modo ISO. */
    private fun restoreISOSlider() {
        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return
        if (!cameraSource.isRunning()) {
            view?.postDelayed({ restoreISOSlider() }, 300)
            return
        }
        // Rango físico de ISO:
        val minIso = cameraSource.getMinISO()
        val maxIso = cameraSource.getMaxISO()
        val stepIso = 100
        val stepsCount = ((maxIso - minIso) / stepIso).coerceAtLeast(1)
        seekBarExposure.max = stepsCount

        // Progreso guardado: si es -1, significa “auto ISO” → leemos getISO() actual
        val savedProg = settingsVm.isoSeekProgress.value ?: -1
        val progress = if (savedProg < 0) {
            // ¿Cuál es el ISO actual de la cámara?
            val curIso = cameraSource.getISO().coerceIn(minIso, maxIso)
            ((curIso - minIso + stepIso / 2) / stepIso).coerceIn(0, stepsCount)
        } else {
            savedProg.coerceIn(0, stepsCount)
        }
        seekBarExposure.progress = progress

        // Si savedProg < 0 (auto), no forzamos nada. Si >=0, aplicamos manual:
        if (savedProg >= 0) {
            val chosenIso = (minIso + progress * stepIso).coerceIn(minIso, maxIso)
            tvEvValue.text = "ISO: $chosenIso"
            cameraSource.setISO(chosenIso)
        } else {
            // modo “auto ISO”
            tvEvValue.text = "ISO: Auto"
            cameraSource.enableAutoISO()
        }
    }

    /** Aplica en la cámara el ISO para el `progress`. */
    private fun applyISOSlider(progress: Int, cameraSource: CameraCalypsoSource) {
        val minIso = cameraSource.getMinISO()
        val maxIso = cameraSource.getMaxISO()
        val stepIso = 100
        val stepsCount = ((maxIso - minIso) / stepIso).coerceAtLeast(1)
        val p = progress.coerceIn(0, stepsCount)
        val isoValue = (minIso + p * stepIso).coerceIn(minIso, maxIso)
        cameraSource.setISO(isoValue)
        tvEvValue.text = "ISO: $isoValue"
    }

    /** Restaura el SeekBar en modo “White Balance”. */
    private fun restoreWbSlider() {
        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return
        if (!cameraSource.isRunning()) {
            view?.postDelayed({ restoreWbSlider() }, 300)
            return
        }
        // Lista de modos AWB disponibles
        val modesList = cameraSource.getAutoWhiteBalanceModesAvailable()
        if (modesList.isEmpty()) {
            tvEvValue.visibility = View.GONE
            seekBarExposure.visibility = View.GONE
            return
        }
        val count = modesList.size
        seekBarExposure.max = count - 1

        // Progreso guardado: si es -1, significa “auto AWB”
        val savedProg = settingsVm.wbSeekProgress.value ?: -1
        val progress = if (savedProg < 0) {
            // Descubrimos el modo actual de la cámara:
            val currentMode = cameraSource.getWhiteBalance()
            modesList.indexOf(currentMode).let { idx -> if (idx < 0) 0 else idx }
        } else {
            savedProg.coerceIn(0, count - 1)
        }
        seekBarExposure.progress = progress

        if (savedProg < 0) {
            // “auto AWB”
            val autoIdx = modesList.indexOf(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
            if (autoIdx >= 0) {
                cameraSource.enableAutoWhiteBalance(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
                tvEvValue.text = "WB: Auto"
            } else {
                // si no hay AWB auto en la lista, simplemente dejo el índice 0
                val mode0 = modesList[0]
                cameraSource.enableAutoWhiteBalance(mode0)
                tvEvValue.text = "WB: ${awbModeName(mode0)}"
            }
        } else {
            // modo manual según “progress”
            val chosenMode = modesList[progress]
            cameraSource.enableAutoWhiteBalance(chosenMode)
            tvEvValue.text = "WB: ${awbModeName(chosenMode)}"
        }
    }

    /** Aplica en la cámara el modo AWB para el `progress`. */
    private fun applyWbSlider(progress: Int, cameraSource: CameraCalypsoSource) {
        val modesList = cameraSource.getAutoWhiteBalanceModesAvailable()
        Log.d("applyWbSlider", "applyWbSlider: modesList = $modesList")
        if (modesList.isEmpty()) return
        val idx = progress.coerceIn(0, modesList.size - 1)
        val chosenMode = modesList[idx]
        cameraSource.enableAutoWhiteBalance(chosenMode)
        tvEvValue.text = "WB: ${awbModeName(chosenMode)}"
    }

    /** Restaura el SeekBar en modo “Exposure Time” (8 valores fijos). */
    private fun restoreEtSlider() {
        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return
        if (!cameraSource.isRunning()) {
            view?.postDelayed({ restoreEtSlider() }, 300)
            return
        }
        val etDenominators = arrayOf(30, 40, 50, 60, 100, 120, 250, 500)
        seekBarExposure.max = etDenominators.size - 1

        // Progreso almacenado:
        val savedProg = settingsVm.etSeekProgress.value ?: 2
        val progress = savedProg.coerceIn(0, etDenominators.size - 1)
        seekBarExposure.progress = progress

        // Etiqueta y aplicación real
        val chosenDen = etDenominators[progress]
        tvEvValue.text = "ET: 1/$chosenDen"
        val ns = (1_000_000_000L / chosenDen)
        cameraSource.setExposureTime(ns)
    }

    /** Aplica en la cámara el tiempo de exposición para el `progress`. */
    private fun applyEtSlider(progress: Int, cameraSource: CameraCalypsoSource) {
        val etDenominators = arrayOf(30, 40, 50, 60, 100, 120, 250, 500)
        val idx = progress.coerceIn(0, etDenominators.size - 1)
        val denom = etDenominators[idx]
        val ns = (1_000_000_000L / denom)
        cameraSource.setExposureTime(ns)
        tvEvValue.text = "ET: 1/$denom"
    }

    /** Mapea código AWB a nombre legible. */
    private fun awbModeName(mode: Int): String {
        return when (mode) {
            CameraCharacteristics.CONTROL_AWB_MODE_AUTO -> "Auto"
            CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT -> "Incandesc."
            CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT -> "Fluoresc."
            CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
            CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Warm Fluor."
            CameraCharacteristics.CONTROL_AWB_MODE_SHADE -> "Shade"
            CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
            CameraCharacteristics.CONTROL_AWB_MODE_OFF -> "Off"
            else -> mode.toString()
        }
    }

    // ------------------------------------------------------
    // (B) restoreUIState  + reaplicación de todos los ajustes
    // ------------------------------------------------------

    private fun restoreUIState() {
        // 1) Restaurar “Manual” / “Auto”
        val manualVisible = settingsVm.isManualVisible.value ?: false
        btnCameraManual.visibility = if (manualVisible) View.VISIBLE else View.GONE

        val autoVisible = settingsVm.isAutoVisible.value ?: false
        btnCameraAuto.visibility = if (autoVisible) View.VISIBLE else View.GONE

        // 2) Restaurar “Stream” / “Camera”
        val streamVisible = settingsVm.isStreamCameraVisible.value ?: false
        val streamVis = if (streamVisible) View.VISIBLE else View.GONE
        btnSettingsStream.visibility = streamVis
        btnSettingsCamera.visibility = streamVis

        // 3) Restaurar “Exposure Compensation Button”
        val expButtonVisible = settingsVm.isExposureButtonVisible.value ?: false
        btnExposureCompensation.visibility = if (expButtonVisible) View.VISIBLE else View.GONE

        // Ajustar α
        val expModeActive = settingsVm.isExposureModeActive.value ?: false
        btnExposureCompensation.alpha = if (expModeActive) 0.4f else 0.8f

        // 4) Restaurar modo “Exposure Compensation”
        if (expModeActive) {
            tvEvValue.visibility = View.VISIBLE
            seekBarExposure.visibility = View.VISIBLE
            restoreExposureSlider()
        } else {
            tvEvValue.visibility = View.GONE
            seekBarExposure.visibility = View.GONE
        }

        // 5) Restaurar “Manual Options” (ISO / ET / WB)
        val manualOptionsVisible = settingsVm.isManualOptionsVisible.value ?: false
        val mOptVis = if (manualOptionsVisible) View.VISIBLE else View.GONE
        btnIso.visibility = mOptVis
        btnExposureTime.visibility = mOptVis
        btnWhiteBalance.visibility = mOptVis

        // 6) Restaurar ISO mode si estaba activo (solo para mostrar slider)
        if (settingsVm.isISOModeActive.value == true) {
            tvEvValue.visibility = View.VISIBLE
            seekBarExposure.visibility = View.VISIBLE
            restoreISOSlider()
        }

        // 7) Restaurar WB mode si estaba activo (solo para mostrar slider)
        if (settingsVm.isWBModeActive.value == true) {
            tvEvValue.visibility = View.VISIBLE
            seekBarExposure.visibility = View.VISIBLE
            restoreWbSlider()
        }

        // 8) Restaurar ET mode si estaba activo (solo para mostrar slider)
        if (settingsVm.isETModeActive.value == true) {
            tvEvValue.visibility = View.VISIBLE
            seekBarExposure.visibility = View.VISIBLE
            restoreEtSlider()
        }

        // ————————————————————————————————————————
        // 9) REAPLICAR TODO lo guardado a la cámara
        // ————————————————————————————————————————
//        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource)
//        if (cameraSource != null && cameraSource.isRunning()) {
//            applyAllSavedSettings(cameraSource)
//        } else {
//            // Si la cámara aún no está lista, reintentamos en 300 ms
//            view?.postDelayed({
//                (genericStream.videoSource as? CameraCalypsoSource)?.let {
//                    if (it.isRunning()) applyAllSavedSettings(it)
//                }
//            }, 300)
//        }
        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource)
        if (cameraSource != null && cameraSource.isRunning()) {
            applyAllSavedSettings(cameraSource)
            // ← Aquí, si no estamos en “Manual Mode”, forzamos AUTO en todo:
            if (settingsVm.isManualMode.value != true) {
                cameraSource.enableAutoExposure()
                cameraSource.enableAutoISO()
                cameraSource.enableAutoWhiteBalance(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
                cameraSource.enableAutoFocus()
            }
        } else {
            view?.postDelayed({
                (genericStream.videoSource as? CameraCalypsoSource)?.let {
                    if (it.isRunning()) {
                        applyAllSavedSettings(it)
                        if (settingsVm.isManualMode.value != true) {
                            it.enableAutoExposure()
                            it.enableAutoISO()
                            it.enableAutoWhiteBalance(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
                            it.enableAutoFocus()
                        }
                    }
                }
            }, 300)
        }
    }

    /**
     * Reaplica en la cámara exactamente lo que el usuario guardó:
     *  1) EV (si ≠ DEFAULT)
     *  2) ISO (si guardó ≥0, o auto si quedó en –1)
     *  3) WB (si guardó ≥0, o auto si quedó en –1)
     *  4) ET (si guardó ≥0)
     */
    private fun applyAllSavedSettings(cameraSource: CameraCalypsoSource) {
        // 1) Exposure Compensation
        val savedEv = settingsVm.seekBarProgress.value ?: DEFAULT_PROGRESS
        if (savedEv != DEFAULT_PROGRESS) {
            applyExposureCompensation(savedEv)
        }

        // 2) ISO
        val savedIsoProg = settingsVm.isoSeekProgress.value ?: -1
        if (savedIsoProg < 0) {
            cameraSource.enableAutoISO()
        } else {
            applyISOSlider(savedIsoProg, cameraSource)
        }

        // 3) White Balance
        val modesList = cameraSource.getAutoWhiteBalanceModesAvailable()
        if (modesList.isNotEmpty()) {
            val savedWbProg = settingsVm.wbSeekProgress.value ?: -1
            if (savedWbProg < 0) {
                // auto AWB
                cameraSource.enableAutoWhiteBalance(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
            } else {
                applyWbSlider(savedWbProg, cameraSource)
            }
        }

        // 4) Exposure Time
        val savedEtProg = settingsVm.etSeekProgress.value ?: 2
        applyEtSlider(savedEtProg, cameraSource)
    }

    // ------------------------------------------------------
    // (C) updateEvText y applyExposureCompensation siguen igual
    // ------------------------------------------------------

    private fun updateEvText(progress: Int) {
        val rawEv = progress - DEFAULT_PROGRESS
        tvEvValue.text = if (rawEv >= 0) "EV: +$rawEv" else "EV: $rawEv"
    }

    private fun applyExposureCompensation(progress: Int) {
        val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return
        if (!cameraSource.isRunning()) {
            view?.postDelayed({ applyExposureCompensation(progress) }, 300)
            return
        }
        val multiplier = 4
        val rawEv = progress - DEFAULT_PROGRESS
        var desiredIndex = rawEv * multiplier
        val minIndex = cameraSource.getMinExposureCompensation()
        val maxIndex = cameraSource.getMaxExposureCompensation()
        desiredIndex = desiredIndex.coerceIn(minIndex, maxIndex)
        cameraSource.setExposureCompensation(desiredIndex)
    }

    // ------------------------------------------------------
    // (D) updateProfileInfo (sin cambios)
    // ------------------------------------------------------
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
