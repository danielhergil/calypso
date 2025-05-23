package com.danihg.calypso.camera

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.danihg.calypso.R
import com.google.android.material.button.MaterialButton

class CameraControlsFragment : Fragment(R.layout.fragment_camera_controls) {

    private lateinit var btnRecord: MaterialButton
    private lateinit var btnStream: MaterialButton
    private lateinit var btnPicture: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnRecord  = view.findViewById(R.id.btnRecordMode)
        btnStream  = view.findViewById(R.id.btnStreamMode)
        btnPicture = view.findViewById(R.id.btnPictureMode)

        // find your CameraFragment by ID
        val cameraFrag = parentFragmentManager
            .findFragmentById(R.id.camera_container) as? CameraFragment
            ?: return

        // toggle recording
        btnRecord.setOnClickListener {
            Log.d("CameraControlsFragment", "btnRecord clicked")
//            cameraFrag.genericStream.let { stream ->
//                if (!stream.isRecording) {
//                    stream.startRecord(/* path */) { status ->
//                        if (status.isRecording) btnRecord.setIconResource(R.drawable.ic_stop)
//                    }
//                } else {
//                    stream.stopRecord()
//                    btnRecord.setIconResource(R.drawable.ic_record_mode)
//                }
//            }
        }

        // toggle streaming
        btnStream.setOnClickListener {
            cameraFrag.genericStream.let { stream ->
                if (!stream.isStreaming) {
                    stream.startStream("rtmp://a.rtmp.youtube.com/live2/j2sh-690b-fg9y-2fah-7444")
                    btnStream.setIconResource(R.drawable.ic_stop)
                } else {
                    stream.stopStream()
                    btnStream.setIconResource(R.drawable.ic_stream_mode)
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