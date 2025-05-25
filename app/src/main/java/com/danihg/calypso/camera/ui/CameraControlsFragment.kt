package com.danihg.calypso.camera.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.CameraViewModel
import com.danihg.calypso.utils.storage.StorageUtils
import com.danihg.calypso.utils.ui.showTemporarySpinnerOn
import com.google.android.material.button.MaterialButton

class CameraControlsFragment : Fragment(R.layout.fragment_camera_controls) {

    // 1) Grab the shared ViewModel
    private val cameraViewModel: CameraViewModel by activityViewModels()
    // 2) Reference its single GenericStream instance
    private val genericStream get() = cameraViewModel.genericStream

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

        // === 1) SYNC BUTTON STATES WITH STREAM & RECORD FLAGS ===
        genericStream.let { stream ->
            // Stream button state
            if (stream.isStreaming) {
                btnStream.setIconResource(R.drawable.ic_stop)
                btnStream.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.calypso_red)
                )
            } else {
                btnStream.setIconResource(R.drawable.ic_stream_mode)
                btnStream.iconTint = null
            }
            // Record button state
            if (stream.isRecording) {
                btnRecord.setIconResource(R.drawable.ic_stop)
                btnRecord.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.calypso_red)
                )
            } else {
                btnRecord.setIconResource(R.drawable.ic_record_mode)
                btnRecord.iconTint = null
            }
        }

        // === 2) RECORD button toggles recording ===
        btnRecord.setOnClickListener {
            val stream = genericStream

            if (sessionId == null) {
                sessionId = StorageUtils.generateSessionId()
            }
            val tempFile = StorageUtils.getTempRecordFile()

            if (!stream.isRecording) {
                stream.startRecord(tempFile.absolutePath) { /* no-op */ }
                showTemporarySpinnerOn(btnRecord, 2000L) {
                    btnRecord.setIconResource(R.drawable.ic_stop)
                    btnRecord.iconTint = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.calypso_red)
                    )
                }
            } else {
                stream.stopRecord()
                showTemporarySpinnerOn(btnRecord, 2000L) {
                    btnRecord.setIconResource(R.drawable.ic_record_mode)
                    btnRecord.iconTint = null
                    sessionId?.let { id ->
                        val final = StorageUtils.renameTempToFinal(id)
                        Log.d("CameraControls", "Saved to $final")
                    }
                }
            }
        }

        // === 3) STREAM button toggles streaming ===
        btnStream.setOnClickListener {
            val stream = genericStream

            if (!stream.isStreaming) {
                sessionId = StorageUtils.generateSessionId()
                stream.startStream("rtmp://a.rtmp.youtube.com/live2/j2sh-690b-fg9y-2fah-7444")
                btnStream.setIconResource(R.drawable.ic_stop)
                btnStream.iconTint = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.calypso_red)
                )
            } else {
                stream.stopStream()
                btnStream.setIconResource(R.drawable.ic_stream_mode)
                btnStream.iconTint = null
                sessionId = null
            }
        }

        // === 4) PICTURE button stub ===
        btnPicture.setOnClickListener {
            Log.d("CameraControlsFragment", "btnPicture clicked")
            // TODO: implement still‐capture via genericStream or GL snapshot
        }
    }
}
