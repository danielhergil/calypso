package com.danihg.calypso.camera

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    // future flags (e.g. showScoreboard) can go here:
    private val _dummyFlag = MutableLiveData<Boolean>()
    val dummyFlag: LiveData<Boolean> = _dummyFlag

    fun setDummyFlag(enabled: Boolean) {
        _dummyFlag.value = enabled
    }
}