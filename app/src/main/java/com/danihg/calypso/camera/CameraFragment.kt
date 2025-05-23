package com.danihg.calypso.camera

import android.os.Build
import android.os.Bundle
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

class CameraFragment : Fragment(R.layout.fragment_camera_preview), ConnectChecker {

    companion object {
        /**
         * Stub factory: later you can add parameters to newInstance(...)
         * and put them into the fragment’s arguments Bundle here.
         */
        fun newInstance(): CameraFragment = CameraFragment()
    }

    // shared ViewModel—ready to observe any future flags
    private val cameraViewModel: CameraViewModel by activityViewModels()

    // your streaming engine
    val genericStream: GenericStream by lazy {
        GenericStream(requireContext(), this).apply {
            getGlInterface().autoHandleOrientation = true
            prepareVideo(1920, 1080, 5_000_000, rotation = 0)
            prepareAudio(32_000, true, 128_000)
        }
    }

    private lateinit var surfaceView: SurfaceView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // bind
        surfaceView = view.findViewById(R.id.surfaceView)

        // surface preview
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!genericStream.isOnPreview) genericStream.startPreview(surfaceView)
            }

            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
                genericStream.getGlInterface().setPreviewResolution(w, h)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (genericStream.isOnPreview) genericStream.stopPreview()
            }
        })

        // observe future flags (no-op until you set dummyFlag)
        cameraViewModel.dummyFlag.observe(viewLifecycleOwner) { enabled ->
            // e.g. add or remove a filter:
            if (enabled) Log.d("CameraFragment", "Filter enabled")
            else Log.d("CameraFragment", "Filter disabled")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        genericStream.release()
    }

    // ----- ConnectChecker callbacks -----
    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() =
        Toast.makeText(requireContext(), "Connected", Toast.LENGTH_SHORT).show()

    override fun onConnectionFailed(reason: String) =
        Toast.makeText(requireContext(), "Connection failed: $reason", Toast.LENGTH_LONG).show()

    override fun onDisconnect() =
        Toast.makeText(requireContext(), "Disconnected", Toast.LENGTH_SHORT).show()

    override fun onAuthError() =
        Toast.makeText(requireContext(), "Auth error", Toast.LENGTH_SHORT).show()

    override fun onAuthSuccess() =
        Toast.makeText(requireContext(), "Auth success", Toast.LENGTH_SHORT).show()

}