package com.danihg.calypso.camera

import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.data.AudioSourceType
import com.danihg.calypso.data.StreamProfile
import com.danihg.calypso.data.VideoSourceType
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.extrasources.CameraUvcSource

class CameraFragment : Fragment(R.layout.fragment_camera_preview) {

    companion object {
        /**
         * Stub factory: later you can add parameters to newInstance(...)
         * and put them into the fragment’s arguments Bundle here.
         */
        fun newInstance(): CameraFragment = CameraFragment()
    }
    private var pendingProfile: StreamProfile? = null

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream
    private lateinit var surfaceView: SurfaceView

    private var currentStreamUrl: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        surfaceView = view.findViewById(R.id.surfaceView)

        // Surface callbacks handle preview start/stop
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {

                if (!genericStream.isOnPreview) {
                    genericStream.startPreview(surfaceView)
                }

                pendingProfile?.let { profile ->
                    applyProfile(profile)
                    pendingProfile = null
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

        cameraViewModel.streamUrl.observe(viewLifecycleOwner) { url ->
            currentStreamUrl = url
        }

        cameraViewModel.loadProfileEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { profile ->
                // Guarda el perfil pendiente…
                pendingProfile = profile
                // Y si el Surface ya es válido, aplícalo YA:
                if (surfaceView.holder.surface.isValid) {
                    applyProfile(profile)
                    pendingProfile = null
                }
            }
        }

        cameraViewModel.applyActiveSettingsEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { settings ->
                applyActiveSettings(settings)
            }
        }
    }

    private fun applyProfile(profile: StreamProfile) {
        genericStream.release()
        Log.d("CameraFragment", "Applying profile: $profile")
        genericStream.setVideoCodec(profile.videoCodec)
        genericStream.prepareVideo(
            width = profile.streamWidth,
            height = profile.streamHeight,
            bitrate = profile.videoBitrate,
            fps = profile.videoFps,
            recordWidth = profile.recordWidth,
            recordHeight = profile.recordHeight,
            recordBitrate = profile.recordBitrate
        )
        genericStream.prepareAudio(
            sampleRate = 48_000,
            isStereo = true,
            bitrate = profile.audioBitrate
        )
        if (!genericStream.isOnPreview) {
            genericStream.startPreview(surfaceView)
        }

        val videoSource = when (profile.videoSource) {
            VideoSourceType.DEVICE_CAMERA -> Camera2Source(requireContext())
            VideoSourceType.USB_CAMERA    -> CameraUvcSource()
        }

        val audioSource = when (profile.audioSource) {
            AudioSourceType.DEVICE_AUDIO -> MicrophoneSource(MediaRecorder.AudioSource.DEFAULT)
            AudioSourceType.MICROPHONE   -> MicrophoneSource(MediaRecorder.AudioSource.MIC)
        }
        genericStream.changeVideoSource(videoSource)
        genericStream.changeAudioSource(audioSource)
        // ADD: Sync settings with ViewModel
        cameraViewModel.setVideoBitrate(profile.videoBitrate)
        cameraViewModel.setStreamUrl(currentStreamUrl)
    }

    private fun applyActiveSettings(settings: CameraViewModel.ActiveSettings) {
        val videoSource = when (settings.videoSourceType) {
            VideoSourceType.DEVICE_CAMERA -> Camera2Source(requireContext())
            VideoSourceType.USB_CAMERA -> CameraUvcSource()
        }

        val audioSource = when (settings.audioSourceType) {
            AudioSourceType.DEVICE_AUDIO -> MicrophoneSource(MediaRecorder.AudioSource.DEFAULT)
            AudioSourceType.MICROPHONE -> MicrophoneSource(MediaRecorder.AudioSource.MIC)
        }

        genericStream.changeVideoSource(videoSource)
        genericStream.changeAudioSource(audioSource)
        Log.d("CameraFragment", "Applying active settings: $settings")
        genericStream.setVideoBitrateOnFly(settings.videoBitrateMbps * 1_000_000)

        // ADD: Sync bitrate with ViewModel
        cameraViewModel.setVideoBitrate(settings.videoBitrateMbps * 1_000_000)
    }
}