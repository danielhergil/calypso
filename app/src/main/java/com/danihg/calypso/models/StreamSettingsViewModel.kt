package com.danihg.calypso.models

import androidx.lifecycle.*
import com.danihg.calypso.data.SettingsProfile
import com.danihg.calypso.data.SettingsProfileRepository

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danihg.calypso.data.StreamConnection

class StreamSettingsViewModel : ViewModel() {

    // Nuevo: si estamos creando (false) o actualizando (true)
    val isUpdateMode = MutableLiveData(false)

    // También puedes mover aquí el editingProfileId:
    val editingProfileId = MutableLiveData<String?>(null)

    // Conexiones RTMP
    val connections = MutableLiveData<MutableList<StreamConnection>>(mutableListOf())
    val selectedConnectionIndex = MutableLiveData(-1)
    val connectionUrl = MutableLiveData("")
    val connectionKey = MutableLiveData("")
    val connectionAlias = MutableLiveData("")

    // Alias de perfil
    val profileAlias = MutableLiveData("")

    // Video Settings
    val videoSource     = MutableLiveData("Device Camera")
    val videoCodec      = MutableLiveData("H264")
    val videoResolution = MutableLiveData("1080p")
    val videoFps        = MutableLiveData(30)
    val videoBitrate    = MutableLiveData(5)

    // Record Settings
    val recordResolution = MutableLiveData("1080p")
    val recordBitrate    = MutableLiveData(5)

    // Audio Settings
    val audioSource  = MutableLiveData("Device Audio")
    val audioBitrate = MutableLiveData(160)

    val isFormVisible = MutableLiveData(false)
}