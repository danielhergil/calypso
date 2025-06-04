package com.danihg.calypso

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.danihg.calypso.data.AudioSourceType
import com.danihg.calypso.data.SettingsProfile
import com.danihg.calypso.data.SettingsProfileRepository
import com.danihg.calypso.data.StreamConnection
import com.danihg.calypso.data.StreamProfile
import com.danihg.calypso.data.VideoSourceType
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.generic.GenericStream

/**
 * CalypsoApp:
 *  - Siempre inicializa el streaming de vídeo en onCreate().
 *  - Guarda la configuración de audio en pendingAudioConfig.
 *  - Solo llama a prepareAudio(...) cuando initializeAudioIfNeeded() es invocado (después de permiso).
 */
class CalypsoApp : Application(), ConnectChecker {

    /**
     * Instancia singleton de GenericStream que vivirá mientras la app esté en memoria.
     * No llamamos a prepareAudio aquí sin comprobar permiso primero.
     */
    val genericStream: GenericStream by lazy {
        GenericStream(this, this)
    }

    /**
     * Guarda la configuración de audio “pendiente” que debemos aplicar
     * en cuanto tengamos RECORD_AUDIO. Si es null, no hay nada que inicializar.
     */
    private var pendingAudioConfig: AudioConfig? = null

    /**
     * Indicador para no intentar inicializar audio varias veces si ya lo hicimos.
     */
    private var audioInitialized = false

    override fun onCreate() {
        super.onCreate()

        // 1) Leer alias del último perfil guardado
        val prefs = getSharedPreferences("stream_prefs", Context.MODE_PRIVATE)
        val savedAlias = prefs.getString("last_loaded_profile", null)

        Log.d("CalypsoApp", "onCreate() → last_loaded_profile_alias=$savedAlias")

        // 2) Configuración mínima: siempre preparamos el vídeo (no necesita permiso)
        genericStream.getGlInterface().autoHandleOrientation = true
        genericStream.getStreamClient().setBitrateExponentialFactor(0.5f)
        // Valores base de vídeo; si cargamos perfil, los sobreescribiremos en fetchProfiles.onLoaded
        genericStream.prepareVideo(width = 1920, height = 1080, bitrate = 5_000_000)
        genericStream.getStreamClient().setReTries(10)

        if (!savedAlias.isNullOrBlank()) {
            // 3) Si existe alias, hacemos fetch de todos los SettingsProfile
            val repo = SettingsProfileRepository()
            repo.fetchProfiles(object : SettingsProfileRepository.FetchCallback {
                override fun onEmpty() {
                    // Sin perfiles → mantenemos la configuración base de vídeo y registramos audio base
                    // guardamos la configuración de audio base para aplicar después del permiso.
                    pendingAudioConfig = AudioConfig(
                        sampleRate = 48_000,
                        stereo = true,
                        audioBitrate = 128_000
                    )
                }

                override fun onError(e: Exception) {
                    Log.e("CalypsoApp", "Error al fetchProfiles: ${e.message}")
                    // Mantenemos configuración base: ya se guardó en pendingAudioConfig arriba
                }

                override fun onLoaded(profiles: List<SettingsProfile>) {
                    val matched = profiles.firstOrNull { it.alias == savedAlias }
                    if (matched == null) {
                        // No hay coincidencia → dejamos base
                        pendingAudioConfig = AudioConfig(
                            sampleRate = 48_000,
                            stereo = true,
                            audioBitrate = 128_000
                        )
                        return
                    }

                    // 4) Tenemos el ID, pedimos el mapa completo
                    repo.fetchProfileById(matched.id) { dataMap, error ->
                        if (dataMap == null) {
                            Log.e("CalypsoApp", "Error en fetchProfileById: $error")
                            // Dejo audio base pendiente
                            pendingAudioConfig = AudioConfig(
                                sampleRate = 48_000,
                                stereo = true,
                                audioBitrate = 128_000
                            )
                            return@fetchProfileById
                        }

                        try {
                            // 5) Reconstruir lista de conexiones
                            val rawConns = dataMap["connections"] as? List<Map<String, Any>> ?: emptyList()
                            val conns = rawConns.map { m ->
                                StreamConnection(
                                    url = m["rtmp_url"] as String,
                                    streamKey = m["streamkey"] as String,
                                    alias = m["alias"] as String
                                )
                            }

                            // 6) URL completa de la conexión seleccionada
                            val selectedAliasFromMap = dataMap["selectedConnectionAlias"] as? String ?: ""
                            val selFullUrl = conns
                                .firstOrNull { it.alias == selectedAliasFromMap }
                                ?.let {
                                    val base = it.url.trimEnd('/')
                                    "$base/${it.streamKey}"
                                } ?: ""

                            // 7) Leer videoSettings
                            val vidMap = (dataMap["videoSettings"] as? Map<*, *>) ?: emptyMap<String, Any>()
                            val vSource = vidMap["source"] as? String ?: "Device Camera"
                            val vCodecStr = vidMap["codec"] as? String ?: "H264"
                            val vRes = vidMap["resolution"] as? String ?: "1080p"
                            val vFps = (vidMap["fps"] as? Number)?.toInt() ?: 30
                            val vBrMbps = (vidMap["bitrateMbps"] as? Number)?.toInt() ?: 5

                            // 8) Leer recordSettings (por si se necesita más tarde)
                            val recMap = (dataMap["recordSettings"] as? Map<*, *>) ?: emptyMap<String, Any>()
                            val rRes = recMap["resolution"] as? String ?: "1080p"
                            val rBrMbps = (recMap["bitrateMbps"] as? Number)?.toInt() ?: 5

                            // 9) Leer audioSettings
                            val audMap = (dataMap["audioSettings"] as? Map<*, *>) ?: emptyMap<String, Any>()
                            val aSource = audMap["source"] as? String ?: "Device Audio"
                            val aBrKbps = (audMap["bitrateKbps"] as? Number)?.toInt() ?: 128

                            // 10) Convertir video: resolución, bitrate, codec, fuente
                            val (streamWidth, streamHeight) = when (vRes) {
                                "720p" -> 1280 to 720
                                "1080p" -> 1920 to 1080
                                "1440p" -> 2560 to 1440
                                else -> 1920 to 1080
                            }
                            val videoBitrateBps = vBrMbps * 1_000_000
                            val videoCodecEnum = if (vCodecStr == "H265") VideoCodec.H265 else VideoCodec.H264
                            val videoSourceType = when (vSource) {
                                "Device Camera" -> VideoSourceType.DEVICE_CAMERA
                                "USB Camera" -> VideoSourceType.USB_CAMERA
                                else -> VideoSourceType.DEVICE_CAMERA
                            }

                            // 11) Convertir audio: bitrate, fuente
                            val audioBitrateBps = aBrKbps * 1_000
                            val audioSourceType = when (aSource) {
                                "Device Audio" -> AudioSourceType.DEVICE_AUDIO
                                "Microphone" -> AudioSourceType.MICROPHONE
                                else -> AudioSourceType.DEVICE_AUDIO
                            }

                            // 12) Construir StreamProfile (si hace falta en algún ViewModel)
                            val streamProfile = StreamProfile(
                                streamWidth = streamWidth,
                                streamHeight = streamHeight,
                                videoBitrate = videoBitrateBps,
                                videoFps = vFps,
                                videoCodec = videoCodecEnum,
                                videoSource = videoSourceType,
                                audioBitrate = audioBitrateBps,
                                audioSource = audioSourceType,
                                recordWidth = when (rRes) {
                                    "720p" -> 1280
                                    "1080p" -> 1920
                                    "1440p" -> 2560
                                    else -> 1920
                                },
                                recordHeight = when (rRes) {
                                    "720p" -> 720
                                    "1080p" -> 1080
                                    "1440p" -> 1440
                                    else -> 1080
                                },
                                recordBitrate = rBrMbps * 1_000_000,
                                rtmpUrl = selFullUrl
                            )

                            Log.d("CalypsoApp", "Perfil cargado: $streamProfile")

                            // 13) Reconfiguramos el vídeo con los valores del perfil
                            genericStream.getGlInterface().autoHandleOrientation = true
                            genericStream.getStreamClient().setBitrateExponentialFactor(0.5f)
                            genericStream.prepareVideo(
                                width = streamWidth,
                                height = streamHeight,
                                bitrate = videoBitrateBps,
                                fps = vFps,
                                recordWidth = streamProfile.recordWidth,
                                recordHeight = streamProfile.recordHeight,
                                recordBitrate = streamProfile.recordBitrate
                            )

                            // 14) Guardamos la configuración de audio pendiente para ejecutar luego
                            pendingAudioConfig = AudioConfig(
                                sampleRate = 48_000,
                                stereo = true,
                                audioBitrate = audioBitrateBps
                            )

                            // 15) Guardar URL de streaming en SharedPreferences para siguiente arranque
                            prefs.edit {
                                putString("last_stream_url", selFullUrl)
                            }

                            Log.d(
                                "CalypsoApp",
                                "Perfil cargado en onCreate(): “${matched.alias}” → " +
                                        "rtmp=$selFullUrl, ${streamWidth}x${streamHeight}@${vFps}fps, " +
                                        "brVideo=$videoBitrateBps, brAudio=$audioBitrateBps"
                            )
                        } catch (e: Exception) {
                            Log.e("CalypsoApp", "Error parseando perfil: ${e.message}")
                            // Si hay fallo, dejamos audio base pendiente
                            pendingAudioConfig = AudioConfig(
                                sampleRate = 48_000,
                                stereo = true,
                                audioBitrate = 128_000
                            )
                        }
                    }
                }
            })
        } else {
            // 1) Inicialización por defecto (sin perfil):
            genericStream.getGlInterface().autoHandleOrientation = true
            genericStream.getStreamClient().setBitrateExponentialFactor(0.5f)
            genericStream.prepareVideo(width = 1920, height = 1080, bitrate = 5_000_000)

            // Preparamos audio base (pero como “pendiente” para que se ejecute
            // solo después de que el usuario haya otorgado permiso).
            pendingAudioConfig = AudioConfig(
                sampleRate = 48_000,
                stereo = true,
                audioBitrate = 160_000
            )
            genericStream.getStreamClient().setReTries(10)
        }
    }

    /**
     * Llamar desde tu Fragment (o Activity) *después* de que se haya confirmado el permiso
     * Manifest.permission.RECORD_AUDIO. Esto aplicará la configuración de audio pendiente
     * (si existe) llamando a prepareAudio(...) una sola vez.
     */
    fun initializeAudioIfNeeded() {
        if (audioInitialized) {
            Log.d("CalypsoApp", "initializeAudioIfNeeded(): ya estaba inicializado.")
            return
        }
        // Comprobar permiso de audio
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("CalypsoApp", "initializeAudioIfNeeded() sin RECORD_AUDIO. Se omite.")
            return
        }
        // Si no hay configuración pendiente, nada que hacer
        val config = pendingAudioConfig
        if (config == null) {
            Log.d("CalypsoApp", "initializeAudioIfNeeded(): no hay AudioConfig pendiente.")
            return
        }
        try {
            genericStream.prepareAudio(
                config.sampleRate,
                config.stereo,
                config.audioBitrate
            )
            audioInitialized = true
            pendingAudioConfig = null
            Log.d(
                "CalypsoApp",
                "prepareAudio() ejecutado con éxito: " +
                        "${config.sampleRate}Hz, stereo=${config.stereo}, br=${config.audioBitrate}"
            )
        } catch (e: Exception) {
            Log.e("CalypsoApp", "Error en prepareAudio() dentro de initializeAudioIfNeeded: ${e.message}")
        }
    }

    // Implementación vacía de ConnectChecker
    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() = Unit
    override fun onConnectionFailed(reason: String) = Unit
    override fun onDisconnect() = Unit
    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit

    /**
     * Pequeña data class interna para guardar la configuración de audio pendiente.
     */
    private data class AudioConfig(
        val sampleRate: Int,
        val stereo: Boolean,
        val audioBitrate: Int
    )
}
