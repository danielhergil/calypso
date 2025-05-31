package com.danihg.calypso.camera.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.CameraViewModel
import com.danihg.calypso.constants.ACTION_START_RECORD
import com.danihg.calypso.constants.ACTION_START_STREAM
import com.danihg.calypso.constants.ACTION_STOP_RECORD
import com.danihg.calypso.constants.ACTION_STOP_STREAM
import com.danihg.calypso.constants.EXTRA_PATH
import com.danihg.calypso.constants.EXTRA_URL
import com.danihg.calypso.services.CameraService
import com.danihg.calypso.utils.storage.StorageUtils
import com.danihg.calypso.utils.ui.showTemporarySpinnerOn
import com.google.android.material.button.MaterialButton

class CameraControlsFragment : Fragment(R.layout.fragment_camera_controls) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnStream: MaterialButton
    private lateinit var btnPicture: MaterialButton

    // holds the session ID generated when streaming (or recording) starts
    private var sessionId: String? = null

    private var currentStreamUrl: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnRecord  = view.findViewById(R.id.btnRecordMode)
        btnStream  = view.findViewById(R.id.btnStreamMode)
        btnPicture = view.findViewById(R.id.btnPictureMode)

        // 1) Forzamos iconos por defecto (para que siempre aparezcan si vienen del XML)
        btnRecord.setIconResource(R.drawable.ic_record_mode)
        btnRecord.iconTint = null

        btnStream.setIconResource(R.drawable.ic_stream_mode)
        btnStream.iconTint = null

        // 2) btnStream deshabilitado hasta que haya URL válida
        btnStream.isEnabled = false
        btnStream.alpha = 0.4f

        cameraViewModel.streamUrl.observe(viewLifecycleOwner) { url ->
            currentStreamUrl = url
            val isValid = url.isNotBlank() && url != "None"
            btnStream.isEnabled = isValid
            btnStream.alpha = if (isValid) 1f else 0.4f
        }

        // 3) Listeners
        btnRecord.setOnClickListener { toggleRecord() }
        btnStream.setOnClickListener { toggleStream() }
        btnPicture.setOnClickListener {
            Log.d("CameraControlsFragment", "btnPicture clicked")
            // TODO: implement still‐capture via genericStream o GL snapshot
        }

        // 4) Sincronizamos iconos con el estado real justo al crear la vista
        syncButtonStates()
    }

    override fun onResume() {
        super.onResume()
        // Cada vez que regresamos a foreground, sincronizamos iconos
        syncButtonStates()
    }

    private fun toggleRecord() {
        val ctx = requireContext()
        val intent = Intent(ctx, CameraService::class.java)

        val stream = genericStream

        if (!stream.isRecording) {
            // === INICIAR grabación ===

            // a) (Opcional) Antes del spinner, podemos atenuar el icono para indicar “loading”:
            btnRecord.setIconResource(R.drawable.ic_record_mode)
            btnRecord.iconTint = null
            btnRecord.alpha = 0.5f  // Atenuado al 50% mientras dura spinner

            // b) Llenamos la sessionId
            sessionId = StorageUtils.generateSessionId()
            val tempPath = StorageUtils.getTempRecordFile().absolutePath

            // c) Enviamos Intent al servicio
            intent.action = ACTION_START_RECORD
            intent.putExtra(EXTRA_PATH, tempPath)
            ContextCompat.startForegroundService(ctx, intent)

            // d) Mostramos spinner 2 s; al terminar, volvemos a sincronizar iconos con isRecording=true
            showTemporarySpinnerOn(btnRecord, 2000L) {
                // Restauramos alpha y pintamos el icono según estado real
                btnRecord.alpha = 1f
                syncButtonStates()  // Como el servicio ya habrá puesto isRecording=true, se mostrará ic_stop+rojo
            }
        } else {
            // === DETENER grabación ===

            // a) Atenuamos icono mientras dura spinner:
            btnRecord.setIconResource(R.drawable.ic_stop)
            btnRecord.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
            btnRecord.alpha = 0.5f

            // b) Enviamos Intent al servicio para остановить la grabación
            intent.action = ACTION_STOP_RECORD
            ContextCompat.startForegroundService(ctx, intent)

            // c) Spinner 2 s; al terminar, restauramos UI según isRecording=false
            showTemporarySpinnerOn(btnRecord, 2000L) {
                btnRecord.alpha = 1f
                syncButtonStates()  // Ahora isRecording=false, así que se pondrá ic_record_mode
            }
        }
    }

    private fun toggleStream() {
        val ctx = requireContext()
        val intent = Intent(ctx, CameraService::class.java)
        val stream = genericStream

        if (!stream.isStreaming) {
            // === INICIAR streaming ===

            // a) Atenuamos icono mientras dura spinner:
            btnStream.setIconResource(R.drawable.ic_stream_mode)
            btnStream.iconTint = null
            btnStream.alpha = 0.5f

            // b) Generamos sessionId (opcional)
            sessionId = StorageUtils.generateSessionId()

            // c) Enviamos Intent para arrancar streaming
            intent.action = ACTION_START_STREAM
            intent.putExtra(EXTRA_URL, currentStreamUrl)
            ContextCompat.startForegroundService(ctx, intent)

            // d) Spinner 2 s; al terminar, restauramos icono según isStreaming=true
            showTemporarySpinnerOn(btnStream, 2000L) {
                btnStream.alpha = 1f
                syncButtonStates()  // Como el servicio ya puso isStreaming=true, se ve ic_stop+rojo
            }
        } else {
            // === DETENER streaming ===

            // a) Atenuamos icono
            btnStream.setIconResource(R.drawable.ic_stop)
            btnStream.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
            btnStream.alpha = 0.5f

            // b) Enviamos Intent para parar streaming
            intent.action = ACTION_STOP_STREAM
            ContextCompat.startForegroundService(ctx, intent)

            // c) Spinner 2 s; al terminar, restauramos icono según isStreaming=false
            showTemporarySpinnerOn(btnStream, 2000L) {
                btnStream.alpha = 1f
                syncButtonStates()  // isStreaming=false → ic_stream_mode
            }
        }
    }

    private fun syncButtonStates() {
        Log.d("CameraControls", "syncButtonStates(): isRecording=${genericStream.isRecording}, isStreaming=${genericStream.isStreaming}")

        // Record
        if (genericStream.isRecording) {
            btnRecord.setIconResource(R.drawable.ic_stop)
            btnRecord.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
        } else {
            btnRecord.setIconResource(R.drawable.ic_record_mode)
            btnRecord.iconTint = null
        }

        // Stream
        if (genericStream.isStreaming) {
            btnStream.setIconResource(R.drawable.ic_stop)
            btnStream.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            )
        } else {
            btnStream.setIconResource(R.drawable.ic_stream_mode)
            btnStream.iconTint = null
        }
    }
}
