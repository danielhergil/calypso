package com.danihg.calypso.camera.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danihg.calypso.data.StreamProfile

class SharedProfileViewModel : ViewModel() {

    // MutableLiveData que contendrá el StreamProfile cargado
    private val _loadedProfile = MutableLiveData<StreamProfile?>()
    val loadedProfile: LiveData<StreamProfile?> = _loadedProfile

    private val _loadedProfileAlias = MutableLiveData<String?>()
    val loadedProfileAlias: LiveData<String?> = _loadedProfileAlias

    // Llamar desde StreamSettingsFragment (o cualquier otro) para graduar el perfil
    fun setProfile(profile: StreamProfile) {
        _loadedProfile.value = profile
    }

    // (Opcional) método para borrar el perfil o marcar que no hay ninguno
    fun clearProfile() {
        _loadedProfile.value = null
    }

    fun setProfileAlias(profileAlias: String) {
        _loadedProfileAlias.value = profileAlias
    }
}