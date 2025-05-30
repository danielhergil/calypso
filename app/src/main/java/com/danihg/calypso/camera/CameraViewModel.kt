package com.danihg.calypso.camera

import android.app.Application
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danihg.calypso.data.Event
import com.danihg.calypso.data.StreamProfile
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.library.generic.GenericStream

class CameraViewModel(
    app: Application
) : AndroidViewModel(app), ConnectChecker {

    // GenericStream is created once and lived in this ViewModel
    val genericStream: GenericStream = GenericStream(getApplication(), this).apply {
        getGlInterface().autoHandleOrientation = true
        getStreamClient().setBitrateExponentialFactor(0.5f)
        // video/audio preparation
        prepareVideo(1920, 1080, 5_000_000)
        prepareAudio(48_000, true, 128_000)
        getStreamClient().setReTries(10)
    }

    override fun onCleared() {
        // when the Activity is truly destroyed, release the stream
        genericStream.release()
        super.onCleared()
    }

    // ConnectChecker callbacks (you can forward these to UI via LiveData if you like)
    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() = Unit
    override fun onConnectionFailed(reason: String) = Unit
    override fun onDisconnect() = Unit
    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit

    /** LOAD PROFILE **/

    private val _loadProfileEvent = MutableLiveData<Event<StreamProfile>>()
    val loadProfileEvent: LiveData<Event<StreamProfile>> = _loadProfileEvent

    fun requestLoadProfile(profile: StreamProfile) {
        _loadProfileEvent.value = Event(profile)
    }

    /** SET RTMP URL **/

    private val _streamUrl = MutableLiveData("None")
    val streamUrl: LiveData<String> = _streamUrl

    fun setStreamUrl(url: String) {
        _streamUrl.value = url
    }
}