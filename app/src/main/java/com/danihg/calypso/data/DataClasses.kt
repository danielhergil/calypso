package com.danihg.calypso.data

import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.video.VideoSource

data class StreamConnection(
    var url: String,
    var streamKey: String,
    var alias: String
)

data class StreamProfile(
    val streamWidth: Int,
    val streamHeight: Int,
    val videoBitrate: Int,
    val videoFps: Int,
    val videoCodec: VideoCodec,
    val videoSource: VideoSourceType,
    val audioBitrate: Int,
    val audioSource: AudioSourceType,
    val recordWidth: Int,
    val recordHeight: Int,
    val recordBitrate: Int,
    val rtmpUrl: String
)

enum class VideoSourceType { DEVICE_CAMERA, USB_CAMERA }

enum class AudioSourceType { DEVICE_AUDIO, MICROPHONE }