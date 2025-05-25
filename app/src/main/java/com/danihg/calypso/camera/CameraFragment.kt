package com.danihg.calypso.camera

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.google.android.material.button.MaterialButton
import com.pedro.common.ConnectChecker
import com.pedro.library.generic.GenericStream
import android.widget.Toast

class CameraFragment : Fragment(R.layout.fragment_camera_preview) {

    companion object {
        /**
         * Stub factory: later you can add parameters to newInstance(...)
         * and put them into the fragment’s arguments Bundle here.
         */
        fun newInstance(): CameraFragment = CameraFragment()
    }

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream
    private lateinit var surfaceView: SurfaceView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        surfaceView = view.findViewById(R.id.surfaceView)

        // Surface callbacks handle preview start/stop
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!genericStream.isOnPreview) {
                    genericStream.startPreview(surfaceView)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                genericStream.getGlInterface().setPreviewResolution(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (genericStream.isOnPreview) {
                    genericStream.stopPreview()
                }
            }
        })
    }
}