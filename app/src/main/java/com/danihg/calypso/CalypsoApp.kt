package com.danihg.calypso

import android.app.Application
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericStream

class CalypsoApp : Application(), ConnectChecker {

    // Instancia singleton de GenericStream que vivirá mientras la app esté en memoria
    // (el primer acceso la construye, y todos la usan).
    val genericStream: GenericStream by lazy {
        GenericStream(this, this).apply {
            // Esta configuración la tenías en tu ViewModel:
            getGlInterface().autoHandleOrientation = true
            getStreamClient().setBitrateExponentialFactor(0.5f)
            prepareVideo(1920, 1080, 5_000_000)
            prepareAudio(48_000, true, 128_000)
            getStreamClient().setReTries(10)
        }
    }

    // Conectar/Desconectar, éxitos/fallos, etc. (implementación vacía o con LiveData si quieres notificar UI)
    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() = Unit
    override fun onConnectionFailed(reason: String) = Unit
    override fun onDisconnect() = Unit
    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit

    override fun onCreate() {
        super.onCreate()
        // Aquí podrías inicializar otras cosas de la app, si hace falta.
    }
}