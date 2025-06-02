// StreamSettingsFragment.kt
package com.danihg.calypso.camera.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.danihg.calypso.R
import com.danihg.calypso.camera.CameraViewModel
import com.danihg.calypso.data.AudioSourceType
import com.danihg.calypso.data.SettingsProfile
import com.danihg.calypso.data.SettingsProfileRepository
import com.danihg.calypso.data.StreamConnection
import com.danihg.calypso.data.StreamProfile
import com.danihg.calypso.data.VideoSourceType
import com.danihg.calypso.models.StreamSettingsViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.pedro.common.VideoCodec

class StreamSettingsFragment : Fragment(R.layout.fragment_stream_settings) {

    private val repo = SettingsProfileRepository()
    private val vm   by viewModels<StreamSettingsViewModel>()

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    // Views
    private lateinit var progressBar: ProgressBar
    private lateinit var btnClose: MaterialButton
    private lateinit var btnAddCenter: MaterialButton
    private lateinit var btnAddBelow: MaterialButton
    private lateinit var scrollContainer: View
    private lateinit var profileListContainer: LinearLayout
    private lateinit var profileForm: LinearLayout
    private lateinit var btnCreateProfile: MaterialButton

    private lateinit var tilConnDropdown: TextInputLayout
    private lateinit var acConn: AutoCompleteTextView
    private lateinit var tilConnUrl: TextInputLayout
    private lateinit var etConnUrl: TextInputEditText
    private lateinit var etConnKey: TextInputEditText
    private lateinit var etConnAlias: TextInputEditText
    private lateinit var btnAddConn: MaterialButton
    private lateinit var btnUpdateConn: MaterialButton
    private lateinit var btnDeleteConn: MaterialButton

    private lateinit var acVideoSource: AutoCompleteTextView
    private lateinit var acCodec: AutoCompleteTextView
    private lateinit var acResolution: AutoCompleteTextView
    private lateinit var acFps: AutoCompleteTextView
    private lateinit var etVideoBitrate: TextInputEditText

    private lateinit var acRecordResolution: AutoCompleteTextView
    private lateinit var etRecordBitrate: TextInputEditText

    private lateinit var acAudioSource: AutoCompleteTextView
    private lateinit var acAudioBitrate: AutoCompleteTextView

    private lateinit var etProfileAlias: TextInputEditText

    private val videoSourceOptions      = listOf("Device Camera", "USB Camera")
    private val codecOptions            = listOf("H264", "H265")
    private val resolutionOptions       = listOf("720p", "1080p", "1440p")
    private val fpsOptions              = listOf("30", "60")
    private val audioSourceOptions      = listOf("Device Audio", "Microphone")
    private val audioBitrateOptions     = listOf("96", "160", "256")
    private val recordResolutionOptions = listOf("720p", "1080p", "1440p")

    // Adapter for RTMP-connections dropdown
    private lateinit var connAdapter: ArrayAdapter<String>


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Bind views
        progressBar           = view.findViewById(R.id.progressBar)
        btnClose              = view.findViewById(R.id.btnCloseStreamSettings)
        btnAddCenter          = view.findViewById(R.id.btnAddProfileCenter)
        btnAddBelow           = view.findViewById(R.id.btnAddProfile)
        scrollContainer       = view.findViewById(R.id.scroll_container)
        profileListContainer  = view.findViewById(R.id.profile_list_container)
        profileForm           = view.findViewById(R.id.profile_form)
        btnCreateProfile      = view.findViewById(R.id.btnCreateProfile)

        tilConnDropdown       = view.findViewById(R.id.tilConnectionDropdown)
        acConn                = view.findViewById(R.id.autoCompleteConnections)
        tilConnUrl            = view.findViewById(R.id.tilConnectionUrl)
        etConnUrl             = view.findViewById(R.id.etConnectionUrl)
        etConnKey             = view.findViewById(R.id.etConnectionKey)
        etConnAlias           = view.findViewById(R.id.etConnectionAlias)
        btnAddConn            = view.findViewById(R.id.btnAddConnection)
        btnUpdateConn         = view.findViewById(R.id.btnUpdateConnection)
        btnDeleteConn         = view.findViewById(R.id.btnDeleteConnection)

        acVideoSource         = view.findViewById(R.id.autoCompleteVideoSource)
        acCodec               = view.findViewById(R.id.autoCompleteCodec)
        acResolution          = view.findViewById(R.id.autoCompleteResolution)
        acFps                 = view.findViewById(R.id.autoCompleteFps)
        etVideoBitrate        = view.findViewById(R.id.etVideoBitrate)

        acRecordResolution    = view.findViewById(R.id.autoCompleteRecordResolution)
        etRecordBitrate       = view.findViewById(R.id.etRecordBitrate)

        acAudioSource         = view.findViewById(R.id.autoCompleteAudioSource)
        acAudioBitrate        = view.findViewById(R.id.autoCompleteAudioBitrate)

        etProfileAlias        = view.findViewById(R.id.etProfileAlias)

        // Observamos el flag y aplicamos UI:
        vm.isFormVisible.observe(viewLifecycleOwner) { formVisible ->
            if (progressBar.visibility == VISIBLE) return@observe

            scrollContainer.visibility = VISIBLE
            if (formVisible) {
                profileListContainer.visibility = GONE
                btnAddCenter.visibility        = GONE
                btnAddBelow.visibility         = GONE
                profileForm.visibility         = VISIBLE
            } else {
                profileForm.visibility         = GONE
                profileListContainer.visibility= VISIBLE
                btnAddCenter.visibility        = VISIBLE
                btnAddBelow.visibility         = GONE
            }
        }

        connAdapter = NoFilterArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        acConn.setAdapter(connAdapter)
        acConn.threshold = 1 // Prevent automatic filtering

        // helper para repoblar el dropdown con "None" + aliases actuales
        fun refreshConnAdapter() {
            val items = listOf("None") + (vm.connections.value ?: emptyList()).map { it.alias }
            connAdapter.clear()
            connAdapter.addAll(items)
            connAdapter.notifyDataSetChanged()
            tilConnDropdown.visibility = if (items.isNotEmpty()) VISIBLE else GONE
        }

        refreshConnAdapter()

        // Cuando toques el campo, vuelve a colocar el adapter y despliega
        acConn.setOnClickListener {
            refreshConnAdapter()
            acConn.showDropDown()
        }

        // También al tocar la flecha / icono:
        acConn.setOnTouchListener { v, _ ->
            refreshConnAdapter()
            acConn.showDropDown()
            false
        }
        tilConnDropdown.visibility = GONE
        btnUpdateConn.visibility   = GONE
        btnDeleteConn.visibility   = GONE

        fun setupDropdown(dropdown: AutoCompleteTextView, items: List<String>) {
            NoFilterArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
                .also { dropdown.setAdapter(it) }
            dropdown.threshold = 1 // Prevent automatic filtering
        }

        setupDropdown(acVideoSource,      videoSourceOptions)
        setupDropdown(acCodec,            codecOptions)
        setupDropdown(acResolution,       resolutionOptions)
        setupDropdown(acFps,              fpsOptions)
        setupDropdown(acAudioSource,      audioSourceOptions)
        setupDropdown(acAudioBitrate,     audioBitrateOptions)
        setupDropdown(acRecordResolution, recordResolutionOptions)

        // 3) Observe VM → UI
        vm.isUpdateMode.observe(viewLifecycleOwner) { updating ->
            btnCreateProfile.text = if (updating) "Update Profile" else "Create Profile"
        }
        // 1) VM → UI: cuando cambia la lista de conexiones
        vm.connections.observe(viewLifecycleOwner) { _ ->
            refreshConnAdapter()
            // si no hay selección o es out-of-bounds, volvemos a “None”
            if (vm.selectedConnectionIndex.value !in (vm.connections.value?.indices ?: emptyList())) {
                vm.selectedConnectionIndex.value = -1
            }
        }

        // 2) VM → UI: cuando cambia la selección
        vm.selectedConnectionIndex.observe(viewLifecycleOwner) { idx ->
            val list = vm.connections.value ?: emptyList()
            if (idx in list.indices) {
                val c = list[idx]
                // pinto alias en el dropdown y datos en los campos
                acConn.setText(c.alias, false)
                etConnUrl.setText(c.url)
                etConnKey.setText(c.streamKey)
                etConnAlias.setText(c.alias)
                btnUpdateConn.visibility = VISIBLE
                btnDeleteConn.visibility = VISIBLE
            } else {
                // “None” seleccionado
                acConn.setText("None", false)
                etConnUrl.text?.clear()
                etConnKey.text?.clear()
                etConnAlias.text?.clear()
                btnUpdateConn.visibility = GONE
                btnDeleteConn.visibility = GONE
            }
        }

        // 3) UI → VM: cuando el usuario elige una opción del dropdown
        acConn.setOnItemClickListener { _, _, pos, _ ->
            // pos==0 => None, pos>0 => índice pos-1 en vm.connections
            vm.selectedConnectionIndex.value = if (pos > 0) pos - 1 else -1
        }

        // 4) Hacer siempre dropdown al tocar o clickar
        val showConnDropdown = {
            refreshConnAdapter()
            acConn.showDropDown()
        }


        vm.profileAlias.observe(viewLifecycleOwner) { alias ->
            // Sincroniza el EditText (ya lo haces) **y** el botón
            btnCreateProfile.isEnabled = alias.isNotBlank()
            btnCreateProfile.alpha     = if (alias.isNotBlank()) 1f else 0.5f
        }
        vm.connectionUrl.observe(viewLifecycleOwner) { if (etConnUrl.text.toString() != it) etConnUrl.setText(it) }
        vm.connectionKey.observe(viewLifecycleOwner) { if (etConnKey.text.toString() != it) etConnKey.setText(it) }
        vm.connectionAlias.observe(viewLifecycleOwner) { if (etConnAlias.text.toString() != it) etConnAlias.setText(it) }
        vm.profileAlias.observe(viewLifecycleOwner)   { if (etProfileAlias.text.toString() != it) etProfileAlias.setText(it) }
        // Video Source
        vm.videoSource.observe(viewLifecycleOwner) { value ->
            if (acVideoSource.text.toString() != value) {
                acVideoSource.setText(value, false)
            }
        }
        acVideoSource.setOnClickListener {
            acVideoSource.showDropDown()
        }
        // Video Codec
        vm.videoCodec.observe(viewLifecycleOwner) { value ->
            if (acCodec.text.toString() != value) {
                acCodec.setText(value, false)
            }
        }
        acCodec.setOnClickListener {
            acCodec.showDropDown()
        }
        vm.videoResolution.observe(viewLifecycleOwner) { value ->
            if (acResolution.text.toString() != value) {
                acResolution.setText(value, false)
            }
        }
        // 3) Listener PARA CUANDO SE HAGA CLICK EN EL CAMPO
        acResolution.setOnClickListener {
            acResolution.showDropDown()
        }

        // Video FPS
        vm.videoFps.observe(viewLifecycleOwner) { value ->
            val s = value.toString()
            if (acFps.text.toString() != s) {
                acFps.setText(s, false)
            }
        }
        acFps.setOnClickListener {
            acFps.showDropDown()
        }
        vm.videoBitrate.observe(viewLifecycleOwner)   { if (etVideoBitrate.text.toString() != it.toString()) etVideoBitrate.setText(it.toString()) }
        // Record Resolution
        vm.recordResolution.observe(viewLifecycleOwner) { value ->
            if (acRecordResolution.text.toString() != value) {
                acRecordResolution.setText(value, false)
            }
        }
        acRecordResolution.setOnClickListener {
            acRecordResolution.showDropDown()
        }
        vm.recordBitrate.observe(viewLifecycleOwner)  { if (etRecordBitrate.text.toString() != it.toString()) etRecordBitrate.setText(it.toString()) }
        // Audio Source
        vm.audioSource.observe(viewLifecycleOwner) { value ->
            if (acAudioSource.text.toString() != value) {
                acAudioSource.setText(value, false)
            }
        }
        acAudioSource.setOnClickListener {
            acAudioSource.showDropDown()
        }
        // Audio Bitrate
        vm.audioBitrate.observe(viewLifecycleOwner) { value ->
            val s = value.toString()
            if (acAudioBitrate.text.toString() != s) {
                acAudioBitrate.setText(s, false)
            }
        }
        acAudioBitrate.setOnClickListener {
            acAudioBitrate.showDropDown()
        }

        // 4) UI → VM
        etConnUrl.doOnTextChanged    { t,_,_,_ -> vm.connectionUrl.value   = t.toString() }
        etConnKey.doOnTextChanged    { t,_,_,_ -> vm.connectionKey.value   = t.toString() }
        etConnAlias.doOnTextChanged  { t,_,_,_ -> vm.connectionAlias.value = t.toString() }
        etProfileAlias.doOnTextChanged{ t,_,_,_ ->
            val s = t?.toString() ?: ""
            vm.profileAlias.value = s
        }

        acConn.setOnClickListener   { showConnDropdown() }
        acConn.setOnTouchListener   { _, _ -> showConnDropdown(); false }
        acVideoSource.setOnItemClickListener { _,_,pos,_ -> vm.videoSource.value = acVideoSource.adapter.getItem(pos) as String }
        acCodec.setOnItemClickListener       { _,_,pos,_ -> vm.videoCodec.value  = acCodec.adapter.getItem(pos) as String }
        acResolution.setOnItemClickListener  { _,_,pos,_ -> vm.videoResolution.value = acResolution.adapter.getItem(pos) as String }
        acFps.setOnItemClickListener         { _,_,pos,_ -> vm.videoFps.value     = (acFps.adapter.getItem(pos) as String).toInt() }
        acRecordResolution.setOnItemClickListener { _,_,pos,_ -> vm.recordResolution.value = acRecordResolution.adapter.getItem(pos) as String }
        acAudioSource.setOnItemClickListener{ _,_,pos,_ -> vm.audioSource.value   = acAudioSource.adapter.getItem(pos) as String }
        acAudioBitrate.setOnItemClickListener{ _,_,pos,_ -> vm.audioBitrate.value  = (acAudioBitrate.adapter.getItem(pos) as String).toInt() }

        etVideoBitrate.doOnTextChanged { t,_,_,_ ->
            vm.videoBitrate.value = t.toString().toIntOrNull() ?: 0
        }
        etRecordBitrate.doOnTextChanged { t,_,_,_ ->
            vm.recordBitrate.value = t.toString().toIntOrNull() ?: 0
        }

        // 5) Botón “Add Connection”
        btnAddConn.setOnClickListener {
            val url   = etConnUrl.text.toString().trim()
            val key   = etConnKey.text.toString().trim()
            val alias = etConnAlias.text.toString().trim()
            if (!url.startsWith("rtmp://") && !url.startsWith("rtmps://")) {
                tilConnUrl.error = "Must start with rtmp:// or rtmps://"
                return@setOnClickListener
            }
            tilConnUrl.error = null

            // reasignamos lista en ViewModel
            val newList = vm.connections.value!!.toMutableList().apply {
                add(StreamConnection(url, key, alias))
            }
            vm.connections.value = newList

            // volvemos a “None”
            vm.selectedConnectionIndex.value = -1

            // limpiar formulario
            etConnUrl.text?.clear()
            etConnKey.text?.clear()
            etConnAlias.text?.clear()
        }

        // 6) Botón “Update Connection”
        btnUpdateConn.setOnClickListener {
            vm.selectedConnectionIndex.value?.takeIf { it >= 0 }?.let { idx ->
                val updated = StreamConnection(
                    etConnUrl.text.toString().trim(),
                    etConnKey.text.toString().trim(),
                    etConnAlias.text.toString().trim()
                )
                val newList = vm.connections.value!!.toMutableList().apply {
                    set(idx, updated)
                }
                vm.connections.value = newList
            }
        }

        // 7) Botón “Delete Connection”
        btnDeleteConn.setOnClickListener {
            vm.selectedConnectionIndex.value?.takeIf { it >= 0 }?.let { idx ->
                // 1) Crea la nueva lista sin el elemento idx
                val newList = vm.connections.value!!.toMutableList().apply {
                    removeAt(idx)
                }
                // 2) Actualiza el ViewModel
                vm.connections.value = newList
                // 3) Fuerza “None” como seleccionado
                vm.selectedConnectionIndex.value = -1
                // 4) Y repuebla el adapter inmediatamente
                refreshConnAdapter()
            }
        }

        // 5) Create / Update profile
        btnCreateProfile.setOnClickListener {
            val alias = vm.profileAlias.value!!.trim()
            val connectionsData = vm.connections.value!!.map { c ->
                val clean = c.url.trimEnd('/')
                hashMapOf(
                    "rtmp_url"   to c.url,
                    "streamkey"  to c.streamKey,
                    "alias"      to c.alias,
                    "full_url"   to "$clean/${c.streamKey}"
                )
            }
            val videoMap = hashMapOf(
                "source"      to vm.videoSource.value!!,
                "codec"       to vm.videoCodec.value!!,
                "resolution"  to vm.videoResolution.value!!,
                "fps"         to vm.videoFps.value!!,
                "bitrateMbps" to vm.videoBitrate.value!!
            )
            val recordMap = hashMapOf(
                "resolution"  to vm.recordResolution.value!!,
                "bitrateMbps" to vm.recordBitrate.value!!
            )
            val audioMap = hashMapOf(
                "source"      to vm.audioSource.value!!,
                "bitrateKbps" to vm.audioBitrate.value!!
            )

            val selectedAlias = vm.selectedConnectionIndex.value
                ?.takeIf { it >= 0 }
                ?.let { vm.connections.value!![it].alias }
                ?: "None"


            val profileData = hashMapOf(
                "alias"           to alias,
                "connections"     to connectionsData,
                "videoSettings"   to videoMap,
                "recordSettings"  to recordMap,
                "audioSettings"   to audioMap,
                "selectedConnectionAlias"  to selectedAlias
            )

            progressBar.visibility = VISIBLE
            val id = vm.editingProfileId.value
            if (vm.isUpdateMode.value == true && id != null) {
                // MODO UPDATE
                repo.updateProfile(id, profileData, object: SettingsProfileRepository.UpdateCallback {
                    override fun onSuccess() {
                        progressBar.visibility = GONE
                        Snackbar.make(requireView(), "Profile Updated", Snackbar.LENGTH_SHORT).show()
                        // Restablecemos el modo para la próxima vez
                        vm.isUpdateMode.value     = false
                        vm.editingProfileId.value = null
                        finishAndReload()
                    }
                    override fun onFailure(e: Exception) {
                        progressBar.visibility = GONE
                        Snackbar.make(requireView(), "Update Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                })
            } else {
                // MODO CREATE
                repo.createProfile(profileData, object: SettingsProfileRepository.CreateCallback {
                    override fun onSuccess(docId: String) {
                        progressBar.visibility = GONE
                        Snackbar.make(requireView(), "Profile Created", Snackbar.LENGTH_SHORT).show()
                        finishAndReload()
                    }
                    override fun onFailure(e: Exception) {
                        progressBar.visibility = GONE
                        Snackbar.make(requireView(), "Create Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                })
            }
        }

        // 6) Other listeners
        btnClose.setOnClickListener { parentFragmentManager.popBackStack() }
        btnAddCenter.setOnClickListener {
            vm.isFormVisible.value = true
            resetForm()
        }
        btnAddBelow.setOnClickListener {
            vm.isFormVisible.value = true
            vm.isUpdateMode.value = false
            vm.editingProfileId.value = null
            resetForm()
        }

        // 7) Initial load
//        btnAddCenter.visibility = GONE
//        btnAddBelow.visibility = GONE
//        progressBar.visibility = VISIBLE
//        repo.fetchProfiles(object: SettingsProfileRepository.FetchCallback {
//            override fun onEmpty() {
//                progressBar.visibility = GONE
//                if (vm.isFormVisible.value != true) showEmptyState()
//            }
//            override fun onError(e: Exception) {
//                progressBar.visibility = GONE
//                if (vm.isFormVisible.value != true) showEmptyState()
//            }
//            override fun onLoaded(profiles: List<SettingsProfile>) {
//                progressBar.visibility = GONE
//                if (vm.isFormVisible.value != true) showProfiles(profiles)
//            }
//        })
        if (vm.isFormVisible.value != true) {
            // estamos en modo lista → cargamos los perfiles
            btnAddCenter.visibility = GONE
            btnAddBelow.visibility = GONE
            progressBar.visibility = VISIBLE
            repo.fetchProfiles(object: SettingsProfileRepository.FetchCallback {
                override fun onEmpty() {
                    progressBar.visibility = GONE
                    showEmptyState()
                }
                override fun onError(e: Exception) {
                    progressBar.visibility = GONE
                    showEmptyState()
                }
                override fun onLoaded(profiles: List<SettingsProfile>) {
                    progressBar.visibility = GONE
                    showProfiles(profiles)
                }
            })
        } else {
            // estamos en modo formulario → restauramos la vista de formulario
            scrollContainer.visibility = VISIBLE
            profileForm.visibility     = VISIBLE
            profileListContainer.visibility = GONE
        }
    }

    private fun showForm() {
        resetForm()
        btnAddCenter.visibility    = GONE
        btnAddBelow.visibility     = GONE
        scrollContainer.visibility = VISIBLE
        profileForm.visibility     = VISIBLE
        profileListContainer.visibility = GONE
    }

    private fun resetForm() {
        vm.profileAlias.value             = ""
        vm.connections.value              = mutableListOf()
        vm.selectedConnectionIndex.value  = -1
        vm.connectionUrl.value            = ""
        vm.connectionKey.value            = ""
        vm.connectionAlias.value          = ""
        vm.videoSource.value              = "Device Camera"
        vm.videoCodec.value               = "H264"
        vm.videoResolution.value          = "1080p"
        vm.videoFps.value                 = 30
        vm.videoBitrate.value             = 5
        vm.recordResolution.value         = "1080p"
        vm.recordBitrate.value            = 5
        vm.audioSource.value              = "Device Audio"
        vm.audioBitrate.value             = 160
    }

    private fun showEmptyState() {
        btnAddCenter.visibility    = VISIBLE
        scrollContainer.visibility = GONE
    }

    private fun showProfiles(profiles: List<SettingsProfile>) {
        btnAddCenter.visibility = GONE
        profileListContainer.removeAllViews()
        profileListContainer.visibility = VISIBLE
        scrollContainer.visibility      = VISIBLE

        val defaultColor = ContextCompat.getColor(requireContext(), R.color.gray)
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.calypso_red)

        profiles.forEachIndexed { index, profile ->
            val card = CardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.padding_small)
                }
                radius = resources.getDimension(R.dimen.card_corner_radius)
                cardElevation = resources.getDimension(R.dimen.card_elevation)
                setCardBackgroundColor(defaultColor)
                setContentPadding(
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium),
                    resources.getDimensionPixelSize(R.dimen.padding_medium)
                )
            }

            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutTransition = android.animation.LayoutTransition()
            }

            container.addView(TextView(requireContext()).apply {
                text = profile.alias
                textSize = 18f
                setPadding(6, 0, 0, resources.getDimensionPixelSize(R.dimen.padding_small))
            })
            container.addView(TextView(requireContext()).apply {
                text = "Stream: ${profile.videoResolution} @${profile.videoFps}fps - Codec: ${profile.codec}"
                setPadding(6, 0, 0, resources.getDimensionPixelSize(R.dimen.padding_small))
            })
            container.addView(TextView(requireContext()).apply {
                text = "Record: ${profile.recordResolution} @${profile.videoFps}fps - Codec: ${profile.codec}"
                setPadding(6, 0, 0, resources.getDimensionPixelSize(R.dimen.padding_small))
            })

            val urlToShow = profile.selectedConnectionFullUrl ?: "None"

            container.addView(TextView(requireContext()).apply {
                text = "RTMP: $urlToShow"
                setPadding(6, 0, 0, resources.getDimensionPixelSize(R.dimen.padding_medium))
            })

            val buttonLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 3f
                visibility = GONE
            }

            listOf("Load", "Edit", "Delete").forEach { text ->
                val btn = MaterialButton(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginEnd = resources.getDimensionPixelSize(R.dimen.padding_small)
                    }
                    this.text = text
                    isAllCaps = false
                }

                when (text) {
                    "Load" -> btn.setOnClickListener {
                        repo.fetchProfileById(profile.id) { data, error ->
                            data?.let { map ->
                                val alias = map["alias"] as? String ?: "Unknown"
                                // conexiones + alias seleccionado
                                val conns = (map["connections"] as? List<Map<String,Any>>)
                                    ?.map { StreamConnection(
                                        it["rtmp_url"]   as String,
                                        it["streamkey"]  as String,
                                        it["alias"]      as String
                                    ) } ?: emptyList()
                                val selectedAlias = (map["selectedConnectionAlias"] as? String)
                                    ?: "None"

                                // video
                                val vs = (map["videoSettings"]   as Map<String,Any>)
                                val vSource = vs["source"]      as String
                                val vCodec  = vs["codec"]       as String
                                val vRes    = vs["resolution"]  as String
                                val vFps    = (vs["fps"]        as Number).toInt()
                                val vBr     = (vs["bitrateMbps"]as Number).toInt()

                                // record
                                val rs   = (map["recordSettings"]  as Map<String,Any>)
                                val rRes = rs["resolution"]  as String
                                val rBr  = (rs["bitrateMbps"]as Number).toInt()

                                // audio
                                val aus    = (map["audioSettings"]  as Map<String,Any>)
                                val aSource = aus["source"]     as String
                                val aBr     = (aus["bitrateKbps"]as Number).toInt()

                                Log.d("StreamSettings", """
                ── Load Profile ─────────────────────────────────────
                alias=$alias
                rtmp=$urlToShow
                selectedConnection=$selectedAlias
                video:   source=$vSource, codec=$vCodec, res=$vRes, fps=$vFps, br(Mbps)=$vBr
                record:  res=$rRes, br(Mbps)=$rBr
                audio:   source=$aSource, br(Kbps)=$aBr
                ────────────────────────────────────────────────────
            """.trimIndent())
                                val (streamWidth, streamHeight) = when (vRes) {
                                    "720p"  -> 1280 to 720
                                    "1080p" -> 1920 to 1080
                                    else    -> 2560 to 1440
                                }

                                val (recordWidth, recordHeight) = when (rRes) {
                                    "720p"  -> 1280 to 720
                                    "1080p" -> 1920 to 1080
                                    else    -> 2560 to 1440
                                }

                                val videoCodec = when (vCodec) {
                                    "H264" -> VideoCodec.H264
                                    else -> VideoCodec.H265
                                }

                                val videoSourceType = when (vSource) {
                                    "Device Camera" -> VideoSourceType.DEVICE_CAMERA
                                    "USB Camera"    -> VideoSourceType.USB_CAMERA
                                    else            -> throw IllegalArgumentException("Unknown source")
                                }

                                val audioSourceType = when (aSource) {
                                    "Device Audio" -> AudioSourceType.DEVICE_AUDIO
                                    "Microphone"   -> AudioSourceType.MICROPHONE
                                    else           -> throw IllegalArgumentException("Unknown source")
                                }

                                val rtmpUrl = conns
                                    .firstOrNull { it.alias == selectedAlias }
                                    ?.let {
                                        val clean = it.url.trimEnd('/')
                                        "$clean/${it.streamKey}"
                                    } ?: ""

                                val streamProfile = StreamProfile(
                                    streamWidth   = streamWidth,
                                    streamHeight  = streamHeight,
                                    videoBitrate  = vBr * 1_000_000,      // de Mbps a bps
                                    videoFps      = vFps,
                                    videoCodec    = videoCodec,
                                    videoSource   = videoSourceType,
                                    audioBitrate  = aBr * 1_000,          // de Kbps a bps
                                    audioSource   = audioSourceType,
                                    recordWidth   = recordWidth,
                                    recordHeight  = recordHeight,
                                    recordBitrate = rBr * 1_000_000,      // de Mbps a bps
                                    rtmpUrl       = rtmpUrl
                                )

                                cameraViewModel.requestLoadProfile(streamProfile)
                                cameraViewModel.setStreamUrl(streamProfile.rtmpUrl)

                                parentFragmentManager.popBackStack()

                            } ?: run {
                                Log.e("StreamSettings", "Error cargando perfil ${profile.id}: $error")
                            }
                        }
                    }
                    "Delete" -> btn.setOnClickListener {
                        progressBar.visibility = VISIBLE
                        repo.deleteProfile(profile.id, object : SettingsProfileRepository.DeleteCallback {
                            override fun onSuccess() {
                                progressBar.visibility = GONE
                                Snackbar.make(requireView(), "Profile Deleted", Snackbar.LENGTH_SHORT).show()
                                finishAndReload()
                            }
                            override fun onFailure(e: Exception) {
                                progressBar.visibility = GONE
                                Snackbar.make(requireView(), "Delete Error", Snackbar.LENGTH_LONG).show()
                            }
                        })
                    }
                    "Edit" -> btn.setOnClickListener {
//                        editingProfileId = profile.id
//                        btnCreateProfile.text = "Update Profile"
                        vm.editingProfileId.value = profile.id
                        vm.isUpdateMode.value     = true
                        vm.isFormVisible.value    = true
                        showForm()

                        repo.fetchProfileById(profile.id) { data, error ->
                            if (data != null) {
                                // Profile Name
                                vm.profileAlias.value = data["alias"] as? String ?: ""

                                // Conexiones
                                val conns = (data["connections"] as? List<Map<String, Any>>)?.map {
                                    StreamConnection(
                                        url       = it["rtmp_url"] as String,
                                        streamKey = it["streamkey"] as String,
                                        alias     = it["alias"] as String
                                    )
                                } ?: emptyList()

                                // 1) Rellena la lista
                                vm.connections.value = conns.toMutableList()

                                // lee el alias guardado:
                                val savedAlias = (data["selectedConnectionAlias"] as? String) ?: "None"
                                // busca su índice en tu lista recién cargada:
                                val idx = conns.indexOfFirst { it.alias == savedAlias }
                                // aplica la selección (−1 = “None”)
                                vm.selectedConnectionIndex.value = if (idx >= 0) idx else -1

                                // Resto de settings...
                                vm.videoSource.value     = (data["videoSettings"] as Map<*,*>)["source"]    as String
                                vm.videoCodec.value      = (data["videoSettings"] as Map<*,*>)["codec"]     as String
                                vm.videoResolution.value = (data["videoSettings"] as Map<*,*>)["resolution"]as String
                                vm.videoFps.value        = ((data["videoSettings"] as Map<*,*>)["fps"]      as Number).toInt()
                                vm.videoBitrate.value    = ((data["videoSettings"] as Map<*,*>)["bitrateMbps"] as Number).toInt()
                                vm.recordResolution.value= (data["recordSettings"] as Map<*,*>)["resolution"] as String
                                vm.recordBitrate.value   = ((data["recordSettings"] as Map<*,*>)["bitrateMbps"] as Number).toInt()
                                vm.audioSource.value     = (data["audioSettings"] as Map<*,*>)["source"] as String
                                vm.audioBitrate.value    = ((data["audioSettings"] as Map<*,*>)["bitrateKbps"] as Number).toInt()
                            }
                        }
                    }
                }

                buttonLayout.addView(btn)
            }

            container.addView(buttonLayout)
            card.addView(container)
            profileListContainer.addView(card)

            card.setOnClickListener {
                profileListContainer.children.forEachIndexed { i, child ->
                    val cv = child as CardView
                    val ll = cv.getChildAt(0) as LinearLayout
                    val btnRow = ll.getChildAt(ll.childCount - 1) as LinearLayout
                    if (i == index) {
                        cv.setCardBackgroundColor(selectedColor)
                        btnRow.visibility = VISIBLE
                    } else {
                        cv.setCardBackgroundColor(defaultColor)
                        btnRow.visibility = GONE
                    }
                }
            }
        }

        btnAddBelow.visibility = VISIBLE
    }

    private fun finishAndReload() {
        vm.isFormVisible.value = false
        profileForm.visibility  = GONE
        btnAddBelow.visibility  = GONE
        btnAddCenter.visibility = GONE
        progressBar.visibility  = VISIBLE
        scrollContainer.visibility      = VISIBLE
        profileListContainer.visibility = VISIBLE

        repo.fetchProfiles(object: SettingsProfileRepository.FetchCallback {
            override fun onEmpty() {
                progressBar.visibility = GONE
                if (vm.isFormVisible.value != true) {
                    showEmptyState()
                }
            }
            override fun onError(e: Exception) {
                progressBar.visibility = GONE
                if (vm.isFormVisible.value != true) {
                    showEmptyState()
                }
            }
            override fun onLoaded(profiles: List<SettingsProfile>) {
                progressBar.visibility = GONE
                if (vm.isFormVisible.value != true) {
                    showProfiles(profiles)
                }
            }
        })
    }

    private class NoFilterArrayAdapter<T>(
        context: Context,
        resource: Int,
        private val items: List<T>
    ) : ArrayAdapter<T>(context, resource, items) {

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults().apply {
                        values = items
                        count = items.size
                    }
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }
    }
}
