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

    private val _videoBitrate = MutableLiveData(5_000_000)
    val videoBitrate: LiveData<Int> = _videoBitrate

    fun setVideoBitrate(bitrate: Int) {
        _videoBitrate.value = bitrate
    }

    // ------------------------------------------------
    // NEW: “apply active settings” event.  We’ll pass a tiny data class with exactly
    // the three fields: videoSource, audioSource, and videoBitrateMbps.
    // We'll wrap it in Event<> so that CameraFragment only handles it once.
    // ------------------------------------------------
    data class ActiveSettings(
        val videoSourceType: com.danihg.calypso.data.VideoSourceType,
        val audioSourceType: com.danihg.calypso.data.AudioSourceType,
        val videoBitrateMbps: Int
    )

    private val _applyActiveSettingsEvent =
        MutableLiveData<Event<ActiveSettings>>()
    val applyActiveSettingsEvent: LiveData<Event<ActiveSettings>> =
        _applyActiveSettingsEvent

    /**
     * Call this from StreamSettingsFragment when the user taps “Apply Changes”.
     */
    fun requestApplyActiveSettings(settings: ActiveSettings) {
        _applyActiveSettingsEvent.value = Event(settings)
    }
}