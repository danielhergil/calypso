package com.danihg.calypso.models

import androidx.lifecycle.*
import com.danihg.calypso.data.SettingsProfile
import com.danihg.calypso.data.SettingsProfileRepository

class StreamSettingsViewModel : ViewModel() {
    private val repo = SettingsProfileRepository()

    // 1) Listado de perfiles
    private val _profiles = MutableLiveData<List<SettingsProfile>>()
    val profiles: LiveData<List<SettingsProfile>> = _profiles

    // 2) Estado de carga
    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    // 3) Errores puntuales
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // 4) Datos de un perfil para editar
    private val _editData = MutableLiveData<Map<String,Any>?>()
    val editData: LiveData<Map<String,Any>?> = _editData

    // 5) ID del perfil en edición (null = modo “crear”)
    val editingProfileId = MutableLiveData<String?>(null)

    /** Carga o recarga la lista de perfiles */
    fun loadProfiles() {
        _loading.value = true
        repo.fetchProfiles(object: SettingsProfileRepository.FetchCallback {
            override fun onEmpty() {
                _loading.value = false
                _profiles.value = emptyList()
            }
            override fun onError(e: Exception) {
                _loading.value = false
                _error.value = e.message
            }
            override fun onLoaded(profiles: List<SettingsProfile>) {
                _loading.value = false
                _profiles.value = profiles
            }
        })
    }

    /** Elimina un perfil y vuelve a recargar */
    fun deleteProfile(id: String) {
        _loading.value = true
        repo.deleteProfile(id, object: SettingsProfileRepository.DeleteCallback {
            override fun onSuccess() {
                loadProfiles()
            }
            override fun onFailure(e: Exception) {
                _loading.value = false
                _error.value = e.message
            }
        })
    }

    /** Crea un perfil y recarga */
    fun createProfile(data: Map<String,Any>) {
        _loading.value = true
        repo.createProfile(data, object: SettingsProfileRepository.CreateCallback {
            override fun onSuccess(docId: String) {
                loadProfiles()
            }
            override fun onFailure(e: Exception) {
                _loading.value = false
                _error.value = e.message
            }
        })
    }

    /** Actualiza un perfil existente y recarga */
    fun updateProfile(data: Map<String,Any>) {
        val id = editingProfileId.value ?: return
        _loading.value = true
        repo.updateProfile(id, data, object: SettingsProfileRepository.UpdateCallback {
            override fun onSuccess() {
                editingProfileId.value = null
                loadProfiles()
            }
            override fun onFailure(e: Exception) {
                _loading.value = false
                _error.value = e.message
            }
        })
    }

    /** Trae un perfil concreto para precargar el formulario */
    fun fetchProfileForEdit(id: String) {
        _loading.value = true
        editingProfileId.value = id
        repo.fetchProfileById(id) { data, error ->
            _loading.postValue(false)
            if (data != null) _editData.postValue(data)
            else _error.postValue(error?.message)
        }
    }
}