package com.danihg.calypso.camera.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class OverlaysViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_OVERLAYS_MENU_VISIBLE = "overlays_menu_visible"
    }

    // Este LiveData se guardará automáticamente en SavedStateHandle:
    val isOverlaysMenuVisible: LiveData<Boolean> =
        savedStateHandle.getLiveData(KEY_OVERLAYS_MENU_VISIBLE, false)

    // Método para cambiar visibilidad y guardarlo:
    fun setOverlaysMenuVisible(visible: Boolean) {
        savedStateHandle[KEY_OVERLAYS_MENU_VISIBLE] = visible
    }
}