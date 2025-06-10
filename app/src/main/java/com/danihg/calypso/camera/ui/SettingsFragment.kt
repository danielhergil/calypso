package com.danihg.calypso.camera.ui


import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
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
import com.pedro.encoder.input.sources.audio.MicrophoneSource

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private companion object {
        const val DEFAULT_PROGRESS = 5   // Para EV (–5..+5)
    }

    // —————————————————————————————————————————
    // 1) ViewModels y resto de referencias (igual que antes)
    // —————————————————————————————————————————
    private val settingsVm: CameraSettingsViewModel by viewModels {
        SavedStateViewModelFactory(requireActivity().application, this)
    }
    private val sharedProfileVm: SharedProfileViewModel by activityViewModels()
    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    // Botones ya existentes ...
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnSettingsStream: MaterialButton
    private lateinit var btnSettingsCamera: MaterialButton

    private lateinit var btnCameraManual: MaterialButton
    private lateinit var btnCameraAuto: MaterialButton
    private lateinit var btnExposureCompensation: MaterialButton

    private lateinit var btnIso: MaterialButton
    private lateinit var btnExposureTime: MaterialButton
    private lateinit var btnWhiteBalance: MaterialButton

    private lateinit var tvProfileInfo: TextView
    private lateinit var tvEvValue: TextView
    private lateinit var seekBarExposure: SeekBar

    // ──────────────────────────────────────────────────────────
    // 2) Nuevas referencias para los botones de Zoom (“+” y “–”)
    // ──────────────────────────────────────────────────────────
    private lateinit var btnZoomIn: MaterialButton
    private lateinit var btnZoomOut: MaterialButton

    // —─────────────────────────────────────────────────────────
    // 2) Nuevas referencias para los controles de audio
    // —─────────────────────────────────────────────────────────
    private lateinit var btnVolumeMenu: MaterialButton
    private lateinit var btnMute: MaterialButton
    private lateinit var volumeExpandable: View
    private lateinit var seekBarVolume: SeekBar

    // ──────────────────────────────────────────────────────────
    // 3) Handler y Runnables para zoom continuo
    // ──────────────────────────────────────────────────────────
    private val zoomHandler = Handler(Looper.getMainLooper())
    private var isZoomingIn = false
    private var isZoomingOut = false


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // —————————————————————————
        // 3.1) Bind de vistas (incluyendo zoom)
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

        // — Bind para los botones de Zoom
        btnZoomIn                 = view.findViewById(R.id.btnZoomIn)
        btnZoomOut                = view.findViewById(R.id.btnZoomOut)

        // —─────────────────────────────────────────────────────────
        // 3.2) Bind de vistas de audio
        // —─────────────────────────────────────────────────────────
        btnVolumeMenu             = view.findViewById(R.id.btnVolumeMenu)
        btnMute                   = view.findViewById(R.id.btnMute)
        volumeExpandable          = view.findViewById(R.id.volume_expandable)
        seekBarVolume             = view.findViewById(R.id.seekBarVolume)

        val root = requireActivity().findViewById<FrameLayout>(R.id.overlays_container)
        val audio = view.findViewById<RelativeLayout>(R.id.audio_controls_container)

        root.post {
            val parentH = root.height
            val percent = 0.08f      // 15%
            val topMarginPx = (parentH * percent).toInt()

            val lp = audio.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = topMarginPx
            lp.gravity = lp.gravity or Gravity.END
            audio.layoutParams = lp
        }

        // —————————————————————————
        // 3.2) Ajuste dinámico tamaño tvProfileInfo (igual que antes)
        // —————————————————————————
        val metrics = requireContext().resources.displayMetrics
        val anchoDp = metrics.widthPixels / metrics.density
        val sizeSp = if (anchoDp < 390f) 8f else 10f
        tvProfileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        // —————————————————————————
        // 3.3) Observamos SharedProfileViewModel (igual que antes)
        // —————————————————————————
        sharedProfileVm.loadedProfile.observe(viewLifecycleOwner) { updateProfileInfo() }
        sharedProfileVm.loadedProfileAlias.observe(viewLifecycleOwner) { updateProfileInfo() }
        // —————————————————————————
        // 3.4) Observadores LiveData en settingsVm (igual que antes)
        // —————————————————————————
        settingsVm.isManualVisible.observe(viewLifecycleOwner) { v ->
            btnCameraManual.visibility = if (v) View.VISIBLE else View.GONE
        }
        settingsVm.isAutoVisible.observe(viewLifecycleOwner) { v ->
            btnCameraAuto.visibility = if (v) View.VISIBLE else View.GONE
        }
        settingsVm.isStreamCameraVisible.observe(viewLifecycleOwner) { v ->
            val visibility = if (v) View.VISIBLE else View.GONE
            btnSettingsStream.visibility = visibility
            btnSettingsCamera.visibility = visibility
        }
        settingsVm.isExposureButtonVisible.observe(viewLifecycleOwner) { v ->
            btnExposureCompensation.visibility = if (v) View.VISIBLE else View.GONE
            settingsVm.isExposureModeActive.value?.let { active ->
                btnExposureCompensation.alpha = if (active) 0.4f else 0.8f
            }
        }
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
        settingsVm.isISOModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreISOSlider()
            } else {
                if (settingsVm.isExposureModeActive.value == false
                    && settingsVm.isWBModeActive.value == false
                    && settingsVm.isETModeActive.value == false
                ) {
                    tvEvValue.visibility = View.GONE
                    seekBarExposure.visibility = View.GONE
                }
            }
        }
        settingsVm.isWBModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreWbSlider()
            } else {
                if (settingsVm.isExposureModeActive.value == false
                    && settingsVm.isISOModeActive.value == false
                    && settingsVm.isETModeActive.value == false
                ) {
                    tvEvValue.visibility = View.GONE
                    seekBarExposure.visibility = View.GONE
                }
            }
        }
        settingsVm.isETModeActive.observe(viewLifecycleOwner) { active ->
            if (active) {
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE
                restoreEtSlider()
            } else {
                if (settingsVm.isExposureModeActive.value == false
                    && settingsVm.isISOModeActive.value == false
                    && settingsVm.isWBModeActive.value == false
                ) {
                    tvEvValue.visibility = View.GONE
                    seekBarExposure.visibility = View.GONE
                }
            }
        }
        settingsVm.isManualOptionsVisible.observe(viewLifecycleOwner) { v ->
            val vis = if (v) View.VISIBLE else View.GONE
            btnIso.visibility = vis
            btnExposureTime.visibility = vis
            btnWhiteBalance.visibility = vis
        }

        // * AUDIO: observar estado de “isVolumeMenuVisible” para mostrar/ocultar panel
        settingsVm.isVolumeMenuVisible.observe(viewLifecycleOwner) { visible ->
            volumeExpandable.visibility = if (visible) View.VISIBLE else View.GONE
        }

        // * AUDIO: observar estado “isMuted” para actualizar icono btnMute
        settingsVm.isMuted.observe(viewLifecycleOwner) { muted ->
            val icon = if (muted) R.drawable.ic_volume_menu else R.drawable.ic_mute
            btnMute.setIconResource(icon)
        }

        // * AUDIO: observar “volumeLevel” para reajustar SeekBar
        settingsVm.volumeLevel.observe(viewLifecycleOwner) { level ->
            // `level` viene en 0f..1f; SeekBar está en 0..100
            val progress = (level * 100f).toInt().coerceIn(0, 100)
            if (seekBarVolume.progress != progress) {
                seekBarVolume.progress = progress
            }
        }

        // Si EV guardado ≠ DEFAULT, aplicamos al inicio
        settingsVm.seekBarProgress.value?.let { p ->
            if (p != DEFAULT_PROGRESS) {
                view.postDelayed({ applyExposureCompensation(p) }, 300)
            }
        }

        // —————————————————————————
        // 4) Listeners de clic “Settings” (igual que antes)
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
                // Ocultamos TODO y reiniciamos flags (igual que antes)
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
                // Alternamos “Stream / Camera” (igual que antes)
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
        // 5) Listeners “Settings Stream” (igual que antes)
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
            requireActivity()
                .findViewById<FrameLayout>(R.id.overlays_container)
                .visibility = View.GONE
        }

        // —————————————————————————
        // 6) Listeners “Settings Camera” (igual que antes)
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
        // 7) Listener “Camera Manual” (igual que antes)
        // —————————————————————————
        btnCameraManual.setOnClickListener {
            settingsVm.setManualMode(true)

            btnCameraManual.visibility = View.GONE
            btnCameraAuto.visibility   = View.GONE
            settingsVm.setManualVisible(false)
            settingsVm.setAutoVisible(false)

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
        // 8) Listener “Camera Auto” (igual que antes)
        // —————————————————————————
        btnCameraAuto.setOnClickListener {
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
        // 9) Listener “Exposure Compensation” (igual que antes)
        // —————————————————————————
        btnExposureCompensation.setOnClickListener {
            val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return@setOnClickListener
            val currentlyActive = settingsVm.isExposureModeActive.value ?: false

            if (!currentlyActive) {
                settingsVm.setExposureModeActive(true)
                settingsVm.setIsISOModeActive(false)
                settingsVm.setIsWBModeActive(false)
                settingsVm.setIsETModeActive(false)

                btnExposureCompensation.alpha = 0.4f
                tvEvValue.visibility = View.VISIBLE
                seekBarExposure.visibility = View.VISIBLE

                val restored = settingsVm.seekBarProgress.value ?: DEFAULT_PROGRESS
                seekBarExposure.max = 10
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
        // 10) CHANGE LISTENER SeekBar (igual que antes)
        // —————————————————————————
        seekBarExposure.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cameraSource = genericStream.videoSource as? CameraCalypsoSource ?: return

                when {
                    settingsVm.isExposureModeActive.value == true -> {
                        settingsVm.setSeekBarProgress(progress)
                        updateEvText(progress)
                        applyExposureCompensation(progress)
                    }
                    settingsVm.isISOModeActive.value == true -> {
                        settingsVm.setIsoSeekProgress(progress)
                        applyISOSlider(progress, cameraSource)
                    }
                    settingsVm.isWBModeActive.value == true -> {
                        settingsVm.setWbSeekProgress(progress)
                        applyWbSlider(progress, cameraSource)
                    }
                    settingsVm.isETModeActive.value == true -> {
                        settingsVm.setEtSeekProgress(progress)
                        applyEtSlider(progress, cameraSource)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { /* no-op */ }
            override fun onStopTrackingTouch(sb: SeekBar) { /* no-op */ }
        })

        // —————————————————————————
        // 11) Listener “ISO” (igual que antes)
        // —————————————————————————
        btnIso.setOnClickListener {
            settingsVm.setExposureModeActive(false)
            settingsVm.setIsWBModeActive(false)
            settingsVm.setIsETModeActive(false)

            val isoActive = settingsVm.isISOModeActive.value ?: false
            if (isoActive) {
                settingsVm.setIsISOModeActive(false)
                tvEvValue.visibility = View.GONE
                seekBarExposure.visibility = View.GONE
            } else {
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
        // 12) Listener “White Balance” (igual que antes)
        // —————————————————————————
        btnWhiteBalance.setOnClickListener {
            settingsVm.setExposureModeActive(false)
            settingsVm.setIsISOModeActive(false)
            settingsVm.setIsETModeActive(false)

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
        // 13) Listener “Exposure Time” (igual que antes)
        // —————————————————————————
        btnExposureTime.setOnClickListener {
            settingsVm.setExposureModeActive(false)
            settingsVm.setIsISOModeActive(false)
            settingsVm.setIsWBModeActive(false)

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

        // —───────────────────────────────────────────
        // 14) Configurar listeners de Zoom (“+” y “–”)
        // —───────────────────────────────────────────

        // Zoom In: cuando se presiona, arrancamos la Runnable que va incrementando el zoom
        btnZoomIn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isZoomingIn = true
                    zoomHandler.post(zoomInRunnable)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isZoomingIn = false
                    zoomHandler.removeCallbacks(zoomInRunnable)
                    true
                }
                else -> false
            }
        }

        // Zoom Out: similar
        btnZoomOut.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isZoomingOut = true
                    zoomHandler.post(zoomOutRunnable)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isZoomingOut = false
                    zoomHandler.removeCallbacks(zoomOutRunnable)
                    true
                }
                else -> false
            }
        }

        // —───────────────────────────────────────────
        // 15) Listeners de AUDIO
        // —───────────────────────────────────────────

        // 15.1) Botón “Volumen principal” (ic_volume_menu): despliega / oculta el panel
        btnVolumeMenu.setOnClickListener {
            val currentlyVisible = settingsVm.isVolumeMenuVisible.value ?: false
            settingsVm.setVolumeMenuVisible(!currentlyVisible)
        }

        // 15.2) Botón “Mute / Unmute”: alterna icono y llama a audioSource.mute()/unMute()
        btnMute.setOnClickListener {
            val audioSource = genericStream.audioSource
            if (audioSource is MicrophoneSource) {
                val currentlyMuted = settingsVm.isMuted.value ?: false
                if (currentlyMuted) {
                    audioSource.unMute()
                    settingsVm.setIsMuted(false)
                } else {
                    audioSource.mute()
                    settingsVm.setIsMuted(true)
                }
            }
        }

        // 15.3) SeekBar vertical de volumen (0..100 → 0f..1f)
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val audioSource = genericStream.audioSource
                if (audioSource is MicrophoneSource) {
                    val newLevel = (progress / 100f).coerceIn(0f, 1f)
                    audioSource.microphoneVolume = newLevel
                    settingsVm.setVolumeLevel(newLevel)
                    // Si estaba en "mute" y el usuario cambia volumen, podríamos auto-desmutear:
                    if (settingsVm.isMuted.value == true) {
                        audioSource.unMute()
                        settingsVm.setIsMuted(false)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // —───────────────────────────────────────────
        // 15) Resto de inicializaciones (igual que antes)
        // —───────────────────────────────────────────
        restoreUIState()
        updateProfileInfo()
    }

    override fun onResume() {
        super.onResume()
        requireActivity()
            .findViewById<FrameLayout>(R.id.overlays_container)
            .visibility = View.VISIBLE
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

        // ────────────────────────────
        // 3) REAPLICAR AJUSTES DE AUDIO
        // ────────────────────────────
        val audioSource = genericStream.audioSource
        if (audioSource is MicrophoneSource) {
            // 3.1) Nivel de volumen guardado
            val savedLevel = settingsVm.volumeLevel.value ?: 1f
            audioSource.microphoneVolume = savedLevel

            // 3.2) Estado MUTE guardado
            val savedMuted = settingsVm.isMuted.value ?: false
            if (savedMuted) audioSource.mute() else audioSource.unMute()

            // 3.3) Actualizar icono btnMute según savedMuted (el observe ya lo hizo, pero nos aseguramos)
            val iconRes = if (savedMuted) R.drawable.ic_volume_menu else R.drawable.ic_mute
            btnMute.setIconResource(iconRes)

            // 3.4) Ajustar SeekBar de volumen a savedLevel (0..100)
            val progress = (savedLevel * 100f).toInt().coerceIn(0, 100)
            seekBarVolume.progress = progress
        }

        // 4) El panel de audio (volume_expandable) se muestra u oculta según el último flag (no persistido)
        val showAudio = settingsVm.isVolumeMenuVisible.value ?: false
        volumeExpandable.visibility = if (showAudio) View.VISIBLE else View.GONE
    }

    /**
     * Reaplica en la cámara exactamente lo que el usuario guardó:
     *  1) EV (si ≠ DEFAULT)
     *  2) ISO (si guardó ≥0, o auto si quedó en –1)
     *  3) WB (si guardó ≥0, o auto si quedó en –1)
     *  4) ET (si guardó ≥0)
     */
    private fun applyAllSavedSettings(cameraSource: CameraCalypsoSource) {
        // ────────── NUEVO: re-aplicar zoom guardado ──────────
        val savedZoom = settingsVm.zoomLevel.value
            ?: cameraSource.getZoomRange().lower
        val range    = cameraSource.getZoomRange()
        // Me aseguro de no salirme de los límites actuales
        val coercedZoom = savedZoom.coerceIn(range.lower, range.upper)
        cameraSource.setZoom(coercedZoom)
        // ────────────────────────────────────────────────────

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

    // Runnable para hacer zoom in progresivo y guardar el nivel en el ViewModel
    private val zoomInRunnable = object : Runnable {
        override fun run() {
            val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return
            if (!cameraSource.isRunning()) {
                // Si la cámara aún no está lista, volvemos a intentar en 100 ms
                zoomHandler.postDelayed(this, 50)
                return
            }

            // 1) Leemos el rango válido de zoom
            val range = cameraSource.getZoomRange()
            val minZ = range.lower
            val maxZ = range.upper

            // 2) Leemos el valor de zoom actual
            val currentZoom = cameraSource.getZoom()

            // 3) Calculamos un paso pequeño (por ejemplo 0.5% del rango total)
            val step = (maxZ - minZ) * 0.003f

            // 4) Definimos `nextZoom`, que es currentZoom + step, coercionándolo al máximo
            val nextZoom = (currentZoom + step).coerceAtMost(maxZ)

            // 5) Aplicamos el nuevo zoom en el hardware
            cameraSource.setZoom(nextZoom)

            // 6) Guardamos ese valor en el ViewModel para persistir estado
            settingsVm.setZoomLevel(nextZoom)

            // 7) Si el usuario sigue manteniendo pulsado, re-lanzamos dentro de 100 ms
            if (isZoomingIn) {
                zoomHandler.postDelayed(this, 50)
            }
        }
    }

    // Runnable para hacer zoom out progresivo y guardar el nivel en el ViewModel
    private val zoomOutRunnable = object : Runnable {
        override fun run() {
            val cameraSource = (genericStream.videoSource as? CameraCalypsoSource) ?: return
            if (!cameraSource.isRunning()) {
                zoomHandler.postDelayed(this, 50)
                return
            }

            // 1) Leemos el rango válido de zoom
            val range = cameraSource.getZoomRange()
            val minZ = range.lower
            val maxZ = range.upper

            // 2) Leemos el valor de zoom actual
            val currentZoom = cameraSource.getZoom()

            // 3) Calculamos un paso pequeño (por ejemplo 0.5% del rango total)
            val step = (maxZ - minZ) * 0.003f

            // 4) Definimos `nextZoom`, que es currentZoom - step, coercionándolo al mínimo
            val nextZoom = (currentZoom - step).coerceAtLeast(minZ)

            // 5) Aplicamos el nuevo zoom en el hardware
            cameraSource.setZoom(nextZoom)

            // 6) Guardamos ese valor en el ViewModel para persistir estado
            settingsVm.setZoomLevel(nextZoom)

            // 7) Si el usuario sigue manteniendo pulsado, re-lanzamos dentro de 100 ms
            if (isZoomingOut) {
                zoomHandler.postDelayed(this, 50)
            }
        }
    }
}
