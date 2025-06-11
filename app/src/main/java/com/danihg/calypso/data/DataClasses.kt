package com.danihg.calypso.data

import com.google.firebase.Timestamp
import com.pedro.common.VideoCodec

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

data class Player(
    val playerName: String = "",
    val number: Int = 0
)

data class Team(
    val name: String = "",
    val alias: String = "",
    val logo: String = "", // URL de la imagen
    val createdAt: Timestamp? = null,
    val players: List<Player> = emptyList()
)