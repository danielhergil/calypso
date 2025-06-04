package com.danihg.calypso.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.danihg.calypso.CalypsoApp
import com.danihg.calypso.MainActivity
import com.danihg.calypso.R
import com.danihg.calypso.constants.ACTION_START_RECORD
import com.danihg.calypso.constants.ACTION_START_STREAM
import com.danihg.calypso.constants.ACTION_STOP_RECORD
import com.danihg.calypso.constants.ACTION_STOP_STREAM
import com.danihg.calypso.constants.EXTRA_PATH
import com.danihg.calypso.constants.EXTRA_URL
import com.danihg.calypso.utils.storage.StorageUtils

private const val CHANNEL_ID = "camera_stream"
private const val NOTIF_ID   = 1

/**
 * Servicio en foreground que ejecuta las llamadas a GenericStream.startRecord/stopRecord
 * y a GenericStream.startStream/stopStream.
 * De este modo el streaming/grabación sigue vivo aunque la Activity o Fragment esté en background.
 */
class CameraService : LifecycleService() {

    // Obtenemos el GenericStream desde CalypsoApp (misma instancia compartida)
    private val realApp by lazy { application as CalypsoApp }
    private val genericStream get() = realApp.genericStream

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Arrancamos el servicio en primer plano con una notificación “lista para grabar/stream”
        startForeground(NOTIF_ID, buildNotification("Listo para grabar/stream"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_START_RECORD -> {
                    // Extraemos la ruta del fichero temporal donde grabaremos
                    val path = intent.getStringExtra(EXTRA_PATH)
                    path?.let {
                        Log.d("CameraService", "Received ACTION_START_RECORD, path=$path")
                        genericStream.startRecord(it) { /* callback si quieres manejar eventos */ }
                        Log.d("CameraService", "After startRecord(), isRecording=${genericStream.isRecording}")
                        updateNotification("Grabando...")
                    }
                }
                ACTION_STOP_RECORD -> {
                    genericStream.stopRecord()
                    // Tras detener la grabación, renombramos el fichero temporal a final
                    val finalPath = StorageUtils.renameTempToFinal(
                        StorageUtils.currentSessionId ?: ""
                    )
                    updateNotification("Grabación guardada: $finalPath")
                }
                ACTION_START_STREAM -> {
                    // Extraemos la URL RTMP para hacer streaming
                    val url = intent.getStringExtra(EXTRA_URL) ?: return@let
                    genericStream.startStream(url)
                    updateNotification("Streameando...")
                }
                ACTION_STOP_STREAM -> {
                    genericStream.stopStream()
                    updateNotification("Stream detenido")
                }
            }
        }
        // START_NOT_STICKY: si Android mata el servicio, no lo reinicia automáticamente con el último Intent.
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Construye la notificación que muestra el estado (“Grabando…” / “Streameando…”, etc.)
     */
    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Calypso Camera")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_record_mode)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    /**
     * Actualiza la notificación cambiando el texto. Simplemente vuelve a notificar
     * con la misma ID (NOTIF_ID), de modo que la notificación se “reemplaza”.
     */
    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    /**
     * Crea el NotificationChannel en Android O+ para nuestro servicio en foreground.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de cámara",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de grabación/streaming"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
