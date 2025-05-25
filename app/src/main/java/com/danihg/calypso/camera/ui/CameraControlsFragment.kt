package com.danihg.calypso.camera.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.danihg.calypso.R
import com.danihg.calypso.camera.CameraFragment
import com.danihg.calypso.utils.storage.StorageUtils
import com.google.android.material.button.MaterialButton
import com.danihg.calypso.utils.ui.showTemporarySpinnerOn

class CameraControlsFragment : Fragment(R.layout.fragment_camera_controls) {

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnStream: MaterialButton
    private lateinit var btnPicture: MaterialButton

    // holds the session ID generated when streaming (or recording) starts
    private var sessionId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnRecord  = view.findViewById(R.id.btnRecordMode)
        btnStream  = view.findViewById(R.id.btnStreamMode)
        btnPicture = view.findViewById(R.id.btnPictureMode)

        // find your CameraFragment by ID
        val cameraFrag = parentFragmentManager
            .findFragmentById(R.id.camera_container) as? CameraFragment
            ?: return

        // === 1) SYNC BUTTON STATES WITH STREAM & RECORD FLAGS ===
        cameraFrag.genericStream.let { stream ->
            // Stream button
            if (stream.isStreaming) {
                btnStream.setIconResource(R.drawable.ic_stop)
                btnStream.iconTint =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.calypso_red)
                    )
            } else {
                btnStream.setIconResource(R.drawable.ic_stream_mode)
                btnStream.iconTint = null
            }
            // Record button
            if (stream.isRecording) {
                btnRecord.setIconResource(R.drawable.ic_stop)
                btnRecord.iconTint =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.calypso_red)
                    )
            } else {
                btnRecord.setIconResource(R.drawable.ic_record_mode)
                btnRecord.iconTint = null
            }
        }

        btnRecord.setOnClickListener {
            cameraFrag.genericStream.let { stream ->
                // ensure we have a session ID
                if (sessionId == null) {
                    sessionId = StorageUtils.generateSessionId()
                }
                val tempFile = StorageUtils.getTempRecordFile()

                if (!stream.isRecording) {
                    // 1) start recording
                    stream.startRecord(tempFile.absolutePath) { status ->
                        // nothing here–we’ll handle UI after delay
                    }
                    // 2) disable + schedule spinner + re-enable
                    showTemporarySpinnerOn(btnRecord, 2000L) {
                        // now set the stop icon tinted red
                        btnRecord.setIconResource(R.drawable.ic_stop)
                        val red = ContextCompat.getColor(requireContext(), R.color.calypso_red)
                        btnRecord.iconTint = ColorStateList.valueOf(red)
                    }
                } else {
                    // 1) stop
                    stream.stopRecord()
                    // 2) disable + schedule spinner + re-enable
                    showTemporarySpinnerOn(btnRecord, 2000L) {
                        // restore record icon in default tint
                        btnRecord.setIconResource(R.drawable.ic_record_mode)
                        btnRecord.iconTint = null
                        // finally rename the file
                        sessionId?.let { id ->
                            val final = StorageUtils.renameTempToFinal(id)
                            Log.d("CameraControls", "Saved to $final")
                        }
                    }
                }
            }
        }

        // STREAM button: generate session ID, start/stop stream
        btnStream.setOnClickListener {
            cameraFrag.genericStream.let { stream ->
                if (!stream.isStreaming) {
                    // generate a new session ID
                    sessionId = StorageUtils.generateSessionId()
                    stream.startStream("rtmp://a.rtmp.youtube.com/live2/j2sh-690b-fg9y-2fah-7444")
                    btnStream.setIconResource(R.drawable.ic_stop)
                    // Tint the stop icon to calypso_red
                    val color = ContextCompat.getColor(requireContext(), R.color.calypso_red)
                    btnStream.iconTint = ColorStateList.valueOf(color)
                } else {
                    stream.stopStream()
                    btnStream.setIconResource(R.drawable.ic_stream_mode)
                    btnStream.iconTint = null
                    sessionId = null
                }
            }
        }

        // picture mode stub
        btnPicture.setOnClickListener {
            Log.d("CameraControlsFragment", "btnPicture clicked")
            // TODO: implement still‐capture
        }
    }
}