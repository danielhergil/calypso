// ActiveStreamSettingsFragment.kt
package com.danihg.calypso.camera.ui

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.Filterable
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.data.AudioSourceType
import com.danihg.calypso.data.VideoSourceType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.extrasources.CameraUvcSource

class ActiveStreamSettingsFragment : Fragment(R.layout.fragment_stream_settings_active) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnClose: MaterialButton
    private lateinit var btnApplyChanges: MaterialButton
    private lateinit var acVideoSource: AutoCompleteTextView
    private lateinit var etVideoBitrate: TextInputEditText
    private lateinit var acAudioSource: AutoCompleteTextView

    private val videoSourceOptions = listOf("Device Camera", "USB Camera")
    private val audioSourceOptions = listOf("Device Audio", "Microphone")

    // Track the selected positions
    private var selectedVideoSourcePosition = -1
    private var selectedAudioSourcePosition = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Bind views
        btnClose = view.findViewById(R.id.btnCloseStreamSettings)
        btnApplyChanges = view.findViewById(R.id.btnApplyChanges)
        acVideoSource = view.findViewById(R.id.autoCompleteVideoSource)
        etVideoBitrate = view.findViewById(R.id.etVideoBitrate)
        acAudioSource = view.findViewById(R.id.autoCompleteAudioSource)

        // 2) Setup dropdowns with NoFilterArrayAdapter
        val videoAdapter = NoFilterArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            videoSourceOptions
        )
        acVideoSource.setAdapter(videoAdapter)
        acVideoSource.threshold = 0  // Show all options immediately

        val audioAdapter = NoFilterArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            audioSourceOptions
        )
        acAudioSource.setAdapter(audioAdapter)
        acAudioSource.threshold = 0  // Show all options immediately

        // 3) Restore state if available
        if (savedInstanceState != null) {
            selectedVideoSourcePosition = savedInstanceState.getInt("videoSourcePosition", -1)
            selectedAudioSourcePosition = savedInstanceState.getInt("audioSourcePosition", -1)
        }

        // 4) Set initial values
        val currentVideoSource = when (genericStream.videoSource) {
            is Camera2Source -> "Device Camera"
            is CameraUvcSource -> "USB Camera"
            else -> "Device Camera"
        }

        val currentAudioSource = when (genericStream.audioSource) {
            is MicrophoneSource -> {
                val mic = genericStream.audioSource as MicrophoneSource
                if (mic.audioSource == MediaRecorder.AudioSource.DEFAULT) "Device Audio" else "Microphone"
            }
            else -> "Device Audio"
        }

        // Set values based on saved state or current stream
        if (selectedVideoSourcePosition != -1) {
            acVideoSource.setText(videoSourceOptions[selectedVideoSourcePosition], false)
        } else {
            acVideoSource.setText(currentVideoSource, false)
            selectedVideoSourcePosition = videoSourceOptions.indexOf(currentVideoSource)
        }

        if (selectedAudioSourcePosition != -1) {
            acAudioSource.setText(audioSourceOptions[selectedAudioSourcePosition], false)
        } else {
            acAudioSource.setText(currentAudioSource, false)
            selectedAudioSourcePosition = audioSourceOptions.indexOf(currentAudioSource)
        }

        // 5) Set initial bitrate value
        val currentBitrate = cameraViewModel.videoBitrate.value ?: 5_000_000
        etVideoBitrate.setText((currentBitrate / 1_000_000).toString())

        // 6) Button listeners
        btnClose.setOnClickListener { parentFragmentManager.popBackStack() }
        btnApplyChanges.setOnClickListener { applySettings() }

        // 7) Dropdown selection listeners to track position
        acVideoSource.setOnItemClickListener { _, _, position, _ ->
            selectedVideoSourcePosition = position
            acVideoSource.setText(videoSourceOptions[position], false)
        }

        acAudioSource.setOnItemClickListener { _, _, position, _ ->
            selectedAudioSourcePosition = position
            acAudioSource.setText(audioSourceOptions[position], false)
        }

        // 8) Click listeners to show full dropdown without filtering
        acVideoSource.setOnClickListener { showDropdown(acVideoSource) }
        acAudioSource.setOnClickListener { showDropdown(acAudioSource) }
    }

    private fun showDropdown(dropdown: AutoCompleteTextView) {
        (dropdown.parent.parent as? TextInputLayout)?.isHintEnabled = false
        dropdown.showDropDown()
    }

    private fun applySettings() {
        val videoSourceType = when (acVideoSource.text.toString()) {
            "Device Camera" -> VideoSourceType.DEVICE_CAMERA
            "USB Camera" -> VideoSourceType.USB_CAMERA
            else -> VideoSourceType.DEVICE_CAMERA
        }

        val audioSourceType = when (acAudioSource.text.toString()) {
            "Device Audio" -> AudioSourceType.DEVICE_AUDIO
            "Microphone" -> AudioSourceType.MICROPHONE
            else -> AudioSourceType.DEVICE_AUDIO
        }

        val bitrateMbps = etVideoBitrate.text.toString().toIntOrNull() ?: 5

        cameraViewModel.requestApplyActiveSettings(
            CameraViewModel.ActiveSettings(
                videoSourceType = videoSourceType,
                audioSourceType = audioSourceType,
                videoBitrateMbps = bitrateMbps
            )
        )
        parentFragmentManager.popBackStack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("videoSourcePosition", selectedVideoSourcePosition)
        outState.putInt("audioSourcePosition", selectedAudioSourcePosition)
    }

    /**
     * Un ArrayAdapter que ignora cualquier filtro, de modo que el dropdown
     * siempre muestre todas las opciones aunque ya haya texto en el campo.
     */
    private class NoFilterArrayAdapter<T>(
        context: Context,
        resource: Int,
        private val items: List<T>
    ) : ArrayAdapter<T>(context, resource, ArrayList(items)), Filterable {

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults().apply {
                        values = items
                        count = items.size
                    }
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    clear()
                    @Suppress("UNCHECKED_CAST")
                    addAll(results?.values as? List<T> ?: emptyList())
                    notifyDataSetChanged()
                }

                override fun convertResultToString(resultValue: Any): CharSequence {
                    return resultValue.toString()
                }
            }
        }
    }
}
