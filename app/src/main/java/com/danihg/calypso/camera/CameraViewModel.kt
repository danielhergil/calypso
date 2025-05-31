package com.danihg.calypso.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.danihg.calypso.CalypsoApp
import com.danihg.calypso.data.Event
import com.danihg.calypso.data.StreamProfile

class CameraViewModel(
    app: Application
) : AndroidViewModel(app) {

    // En lugar de instanciar GenericStream aquí, lo cogemos del Application:
    private val realApp = getApplication<CalypsoApp>()
    val genericStream = realApp.genericStream

    // Resto de tu ViewModel (LiveData, requestLoadProfile, setStreamUrl, etc.)
    private val _loadProfileEvent = MutableLiveData<Event<StreamProfile>>()
    val loadProfileEvent: LiveData<Event<StreamProfile>> = _loadProfileEvent

    fun requestLoadProfile(profile: StreamProfile) {
        _loadProfileEvent.value = Event(profile)
    }

    private val _streamUrl = MutableLiveData("None")
    val streamUrl: LiveData<String> = _streamUrl

    fun setStreamUrl(url: String) {
        _streamUrl.value = url
    }
}