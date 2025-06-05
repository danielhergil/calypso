package com.danihg.calypso.camera.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class CameraSettingsViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_SEEK_PROGRESS = "seek_bar_progress"
        private const val KEY_EXPOSURE_MODE = "exposure_mode_active"

        // Ahora los progesos de ISO y WB arrancan en -1 = “no he tocado nada, que quede en auto”
        private const val KEY_ISO_PROGRESS = "iso_seek_progress"
        private const val KEY_ISO_MODE = "iso_mode_active"

        private const val KEY_WB_PROGRESS = "wb_seek_progress"
        private const val KEY_WB_MODE = "wb_mode_active"

        // El ET (Exposure Time) sí arranca en índice 2 = “1/50” por defecto
        private const val KEY_ET_PROGRESS = "et_seek_progress"
        private const val KEY_ET_MODE = "et_mode_active"

        // Valores por defecto:
        private const val DEFAULT_PROGRESS = 5          // EV = 5  => EV=0 real
        private const val DEFAULT_ISO_PROGRESS = -1     // -1 = “auto”
        private const val DEFAULT_WB_PROGRESS = -1      // -1 = “auto AWB”
        private const val DEFAULT_ET_PROGRESS = 2       // índice 2 => “1/50”
    }

    // 1) SeekBar de “Exposure Compensation” (persistido):
    val seekBarProgress: LiveData<Int> =
        savedStateHandle.getLiveData(KEY_SEEK_PROGRESS, DEFAULT_PROGRESS)

    // 2) Estado “Exposure mode” (persistido):
    val isExposureModeActive: LiveData<Boolean> =
        savedStateHandle.getLiveData(KEY_EXPOSURE_MODE, false)

    // 3) SeekBar de “ISO” (persistido) + estado ISO mode:
    //    - Ahora arranca en -1 (no tocado, autoISO)
    val isoSeekProgress: LiveData<Int> =
        savedStateHandle.getLiveData(KEY_ISO_PROGRESS, DEFAULT_ISO_PROGRESS)
    val isISOModeActive: LiveData<Boolean> =
        savedStateHandle.getLiveData(KEY_ISO_MODE, false)

    // 4) SeekBar de “White Balance” (persistido) + estado WB mode:
    //    - Ahora arranca en -1 (no tocado, AWB auto)
    val wbSeekProgress: LiveData<Int> =
        savedStateHandle.getLiveData(KEY_WB_PROGRESS, DEFAULT_WB_PROGRESS)
    val isWBModeActive: LiveData<Boolean> =
        savedStateHandle.getLiveData(KEY_WB_MODE, false)

    // 5) SeekBar de “Exposure Time” (persistido) + estado ET mode:
    val etSeekProgress: LiveData<Int> =
        savedStateHandle.getLiveData(KEY_ET_PROGRESS, DEFAULT_ET_PROGRESS)
    val isETModeActive: LiveData<Boolean> =
        savedStateHandle.getLiveData(KEY_ET_MODE, false)

    // 6) Otros flags de visibilidad (no persistidos):
    private val _isExposureButtonVisible = MutableLiveData(false)
    val isExposureButtonVisible: LiveData<Boolean> = _isExposureButtonVisible

    private val _isManualVisible = MutableLiveData(false)
    val isManualVisible: LiveData<Boolean> = _isManualVisible

    private val _isAutoVisible = MutableLiveData(false)
    val isAutoVisible: LiveData<Boolean> = _isAutoVisible

    private val _isStreamCameraVisible = MutableLiveData(false)
    val isStreamCameraVisible: LiveData<Boolean> = _isStreamCameraVisible

    // 7) Flag para mostrar/ocultar los 3 botones de “Manual Options” (ISO, ET, WB)
    private val _isManualOptionsVisible = MutableLiveData(false)
    val isManualOptionsVisible: LiveData<Boolean> = _isManualOptionsVisible

    // 8) Nuevo flag “estoy en modo Manual” (solo para controlar cuándo aplicar ISO en automático):
    private val _isManualMode = MutableLiveData(false)
    val isManualMode: LiveData<Boolean> = _isManualMode

    // ------------------------------------------------------
    // Métodos para modificar el estado / SavedStateHandle
    // ------------------------------------------------------

    // --- Exposure Compensation ---
    fun setSeekBarProgress(progress: Int) {
        savedStateHandle[KEY_SEEK_PROGRESS] = progress
    }
    fun setExposureModeActive(active: Boolean) {
        savedStateHandle[KEY_EXPOSURE_MODE] = active
    }
    fun setExposureButtonVisible(visible: Boolean) {
        _isExposureButtonVisible.value = visible
    }

    // --- ISO Mode ---
    fun setIsoSeekProgress(progress: Int) {
        savedStateHandle[KEY_ISO_PROGRESS] = progress
    }
    fun setIsISOModeActive(active: Boolean) {
        savedStateHandle[KEY_ISO_MODE] = active
    }

    // --- White Balance Mode ---
    fun setWbSeekProgress(progress: Int) {
        savedStateHandle[KEY_WB_PROGRESS] = progress
    }
    fun setIsWBModeActive(active: Boolean) {
        savedStateHandle[KEY_WB_MODE] = active
    }

    // --- Exposure Time Mode ---
    fun setEtSeekProgress(progress: Int) {
        savedStateHandle[KEY_ET_PROGRESS] = progress
    }
    fun setIsETModeActive(active: Boolean) {
        savedStateHandle[KEY_ET_MODE] = active
    }

    // --- Otros flags de visibilidad ---
    fun setManualVisible(visible: Boolean) {
        _isManualVisible.value = visible
    }
    fun setAutoVisible(visible: Boolean) {
        _isAutoVisible.value = visible
    }
    fun setStreamCameraVisible(visible: Boolean) {
        _isStreamCameraVisible.value = visible
    }
    fun setManualOptionsVisible(visible: Boolean) {
        _isManualOptionsVisible.value = visible
    }

    // --- “Modo Manual” global ---
    fun setManualMode(active: Boolean) {
        _isManualMode.value = active
    }
}
