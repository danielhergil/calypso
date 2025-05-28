package com.danihg.calypso.camera.ui

import android.animation.LayoutTransition
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.danihg.calypso.R
import com.danihg.calypso.data.SettingsProfile
import com.danihg.calypso.data.SettingsProfileRepository
import com.danihg.calypso.data.StreamConnection
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class StreamSettingsFragment : Fragment(R.layout.fragment_stream_settings) {

    private val repo = SettingsProfileRepository()

    // ---- Views ----
    private lateinit var btnClose: MaterialButton
    private lateinit var btnAddCenter: MaterialButton
    private lateinit var scrollContainer: View
    private lateinit var profileListContainer: LinearLayout
    private lateinit var btnAddBelow: MaterialButton
    private lateinit var profileForm: LinearLayout
    private lateinit var btnCreateProfile: MaterialButton

    // Stream Connections
    private lateinit var tilConnectionDropdown: TextInputLayout
    private lateinit var autoCompleteConnections: AutoCompleteTextView
    private lateinit var tilConnectionUrl: TextInputLayout
    private lateinit var etConnectionUrl: TextInputEditText
    private lateinit var etConnectionKey: TextInputEditText
    private lateinit var etConnectionAlias: TextInputEditText
    private lateinit var btnAddConnection: MaterialButton
    private lateinit var btnUpdateConnection: MaterialButton
    private lateinit var btnDeleteConnection: MaterialButton

    // Video Settings
    private lateinit var tilVideoSource: TextInputLayout
    private lateinit var autoCompleteVideoSource: AutoCompleteTextView
    private lateinit var tilVideoCodec: TextInputLayout
    private lateinit var autoCompleteCodec: AutoCompleteTextView
    private lateinit var tilVideoResolution: TextInputLayout
    private lateinit var autoCompleteResolution: AutoCompleteTextView
    private lateinit var tilVideoFps: TextInputLayout
    private lateinit var autoCompleteFps: AutoCompleteTextView
    private lateinit var tilVideoBitrate: TextInputLayout
    private lateinit var etVideoBitrate: TextInputEditText

    // Record Settings
    private lateinit var tilRecordResolution: TextInputLayout
    private lateinit var autoCompleteRecordResolution: AutoCompleteTextView
    private lateinit var tilRecordBitrate: TextInputLayout
    private lateinit var etRecordBitrate: TextInputEditText

    // Audio Settings
    private lateinit var tilAudioSource: TextInputLayout
    private lateinit var autoCompleteAudioSource: AutoCompleteTextView
    private lateinit var tilAudioBitrate: TextInputLayout
    private lateinit var autoCompleteAudioBitrate: AutoCompleteTextView

    private lateinit var etProfileAlias: TextInputEditText
    private var isAliasValid = false

    // In-memory data
    private val connections = mutableListOf<StreamConnection>()
    private lateinit var connectionsAdapter: ArrayAdapter<String>
    private var selectedConnectionIndex = -1

    private var editingProfileId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Bind all views
        btnClose             = view.findViewById(R.id.btnCloseStreamSettings)
        btnAddCenter         = view.findViewById(R.id.btnAddProfileCenter)
        scrollContainer      = view.findViewById(R.id.scroll_container)
        profileListContainer = view.findViewById(R.id.profile_list_container)
        btnAddBelow          = view.findViewById(R.id.btnAddProfile)
        profileForm          = view.findViewById(R.id.profile_form)
        btnCreateProfile     = view.findViewById(R.id.btnCreateProfile)

        tilConnectionDropdown    = view.findViewById(R.id.tilConnectionDropdown)
        autoCompleteConnections  = view.findViewById(R.id.autoCompleteConnections)
        tilConnectionUrl         = view.findViewById(R.id.tilConnectionUrl)
        etConnectionUrl          = view.findViewById(R.id.etConnectionUrl)
        etConnectionKey          = view.findViewById(R.id.etConnectionKey)
        etConnectionAlias        = view.findViewById(R.id.etConnectionAlias)
        btnAddConnection         = view.findViewById(R.id.btnAddConnection)
        btnUpdateConnection      = view.findViewById(R.id.btnUpdateConnection)
        btnDeleteConnection      = view.findViewById(R.id.btnDeleteConnection)

        tilVideoSource          = view.findViewById(R.id.tilVideoSource)
        autoCompleteVideoSource = view.findViewById(R.id.autoCompleteVideoSource)
        tilVideoCodec           = view.findViewById(R.id.tilVideoCodec)
        autoCompleteCodec  = view.findViewById(R.id.autoCompleteCodec)
        tilVideoResolution      = view.findViewById(R.id.tilVideoResolution)
        autoCompleteResolution  = view.findViewById(R.id.autoCompleteResolution)
        tilVideoFps             = view.findViewById(R.id.tilVideoFps)
        autoCompleteFps         = view.findViewById(R.id.autoCompleteFps)
        tilVideoBitrate         = view.findViewById(R.id.tilVideoBitrate)
        etVideoBitrate          = view.findViewById(R.id.etVideoBitrate)

        tilRecordResolution         = view.findViewById(R.id.tilRecordResolution)
        autoCompleteRecordResolution = view.findViewById(R.id.autoCompleteRecordResolution)
        tilRecordBitrate            = view.findViewById(R.id.tilRecordBitrate)
        etRecordBitrate             = view.findViewById(R.id.etRecordBitrate)

        tilAudioSource           = view.findViewById(R.id.tilAudioSource)
        autoCompleteAudioSource  = view.findViewById(R.id.autoCompleteAudioSource)
        tilAudioBitrate          = view.findViewById(R.id.tilAudioBitrate)
        autoCompleteAudioBitrate = view.findViewById(R.id.autoCompleteAudioBitrate)

        etProfileAlias           = view.findViewById(R.id.etProfileAlias)

        // 2) Setup adapters
        connectionsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        autoCompleteConnections.setAdapter(connectionsAdapter)
        tilConnectionDropdown.visibility = View.GONE
        btnUpdateConnection.visibility = View.GONE
        btnDeleteConnection.visibility = View.GONE

        arrayOf("Device Camera","USB Camera").let {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, it).also { adapter ->
                autoCompleteVideoSource.setAdapter(adapter)
                autoCompleteVideoSource.setText(it[0], false)
            }
        }
        arrayOf("H264","H265").let {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, it).also { adapter ->
                autoCompleteCodec.setAdapter(adapter)
                autoCompleteCodec.setText(it[0], false)
            }
        }
        arrayOf("720p","1080p","1440p").let {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, it).also { adapter ->
                autoCompleteResolution.setAdapter(adapter)
                autoCompleteResolution.setText("1080p", false)
                autoCompleteRecordResolution.setAdapter(adapter)
                autoCompleteRecordResolution.setText("1080p", false)
            }
        }
        arrayOf("30","60").let {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, it).also { adapter ->
                autoCompleteFps.setAdapter(adapter)
                autoCompleteFps.setText(it[0], false)
            }
        }
        arrayOf("Device Audio","Microphone").let {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, it).also { adapter ->
                autoCompleteAudioSource.setAdapter(adapter)
                autoCompleteAudioSource.setText(it[0], false)
            }
        }
        arrayOf("96","160","256").let {
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, it).also { adapter ->
                autoCompleteAudioBitrate.setAdapter(adapter)
                autoCompleteAudioBitrate.setText("160", false)
            }
        }

        etProfileAlias.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int){}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnCreateProfile.isEnabled = !s.isNullOrBlank()
                if (btnCreateProfile.isEnabled) btnCreateProfile.alpha = 1f
            }
            override fun afterTextChanged(s: Editable?){}
        })

        // 3) Stream connections listeners (unchanged)
        btnAddConnection.setOnClickListener {
            val url = etConnectionUrl.text.toString().trim()
            val key = etConnectionKey.text.toString().trim()
            val alias = etConnectionAlias.text.toString().trim()
            if (!url.startsWith("rtmp://") && !url.startsWith("rtmps://")) {
                tilConnectionUrl.error = "Must start with rtmp:// or rtmps://"
                return@setOnClickListener
            }
            tilConnectionUrl.error = null
            connections.add(StreamConnection(url, key, alias))
            updateDropdownAdapter()
            tilConnectionDropdown.visibility = View.VISIBLE
            clearConnectionFields()
        }
        autoCompleteConnections.setOnItemClickListener { _, _, pos, _ ->
            selectedConnectionIndex = pos
            btnUpdateConnection.visibility = View.VISIBLE
            btnDeleteConnection.visibility = View.VISIBLE
            val c = connections[pos]
            etConnectionUrl.setText(c.url)
            etConnectionKey.setText(c.streamKey)
            etConnectionAlias.setText(c.alias)
        }
        btnUpdateConnection.setOnClickListener {
            if (selectedConnectionIndex >= 0) {
                val updated = StreamConnection(
                    etConnectionUrl.text.toString().trim(),
                    etConnectionKey.text.toString().trim(),
                    etConnectionAlias.text.toString().trim()
                )
                connections[selectedConnectionIndex] = updated
                updateDropdownAdapter()
                autoCompleteConnections.setText(updated.alias, false)
            }
        }
        btnDeleteConnection.setOnClickListener {
            if (selectedConnectionIndex >= 0) {
                connections.removeAt(selectedConnectionIndex)
                updateDropdownAdapter()
                selectedConnectionIndex = -1
                autoCompleteConnections.setText("", false)
                clearConnectionFields()
                btnUpdateConnection.visibility = View.GONE
                btnDeleteConnection.visibility = View.GONE
                if (connections.isEmpty()) tilConnectionDropdown.visibility = View.GONE
            }
        }

        // 4) Enable Create only when alias entered
        etConnectionAlias.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnCreateProfile.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 5) Create Profile via repository
        btnCreateProfile.setOnClickListener {
            // 1) Recopilar datos del formulario
            val alias = etProfileAlias.text.toString().trim()
            val connectionsData = connections.map { conn ->
                val clean = conn.url.trimEnd('/')
                hashMapOf(
                    "rtmp_url"  to conn.url,
                    "streamkey" to conn.streamKey,
                    "alias"     to conn.alias,
                    "full_url"  to "$clean/${conn.streamKey}"
                )
            }
            val videoMap = hashMapOf(
                "source"      to autoCompleteVideoSource.text.toString(),
                "codec"       to autoCompleteCodec.text.toString(),
                "resolution"  to autoCompleteResolution.text.toString(),
                "fps"         to autoCompleteFps.text.toString().toInt(),
                "bitrateMbps" to etVideoBitrate.text.toString().toInt()
            )
            val recordMap = hashMapOf(
                "resolution"  to autoCompleteRecordResolution.text.toString(),
                "bitrateMbps" to etRecordBitrate.text.toString().toInt()
            )
            val audioMap = hashMapOf(
                "source"      to autoCompleteAudioSource.text.toString(),
                "bitrateKbps" to autoCompleteAudioBitrate.text.toString().toInt()
            )
            val profileData = hashMapOf(
                "alias"           to alias,
                "connections"     to connectionsData,
                "videoSettings"   to videoMap,
                "recordSettings"  to recordMap,
                "audioSettings"   to audioMap
            )

            val finishAndReload = {
                // 1) Oculta el formulario y los botones de Add
                profileForm.visibility     = View.GONE
                btnAddBelow.visibility     = View.GONE
                btnAddCenter.visibility    = View.GONE
                // 2) Muestra la lista de cards
                scrollContainer.visibility = View.VISIBLE
                profileListContainer.visibility = View.VISIBLE
                // 3) Recarga la lista
                repo.fetchProfiles(object : SettingsProfileRepository.FetchCallback {
                    override fun onEmpty() = showEmptyState()
                    override fun onError(e: Exception) = showEmptyState()
                    override fun onLoaded(profiles: List<SettingsProfile>) = showProfiles(profiles)
                })
            }

            if (editingProfileId != null) {
                // UPDATE
                repo.updateProfile(editingProfileId!!, profileData, object : SettingsProfileRepository.UpdateCallback {
                    override fun onSuccess() {
                        finishAndReload()
                        editingProfileId = null
                        btnCreateProfile.text = "Create Profile"
                    }
                    override fun onFailure(e: Exception) {
                        Toast.makeText(requireContext(),
                            "Error al actualizar perfil", Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                // CREATE
                repo.createProfile(profileData, object : SettingsProfileRepository.CreateCallback {
                    override fun onSuccess(docId: String) {
                        finishAndReload()
                    }
                    override fun onFailure(e: Exception) {
                        Toast.makeText(requireContext(),
                            "Error al crear perfil", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        // 6) Other listeners
        btnClose.setOnClickListener { parentFragmentManager.popBackStack() }
        btnAddCenter.setOnClickListener { showForm() }
        btnAddBelow.setOnClickListener { showForm() }

        // 7) Load existing
        repo.fetchProfiles(object: SettingsProfileRepository.FetchCallback {
            override fun onEmpty() = showEmptyState()
            override fun onError(e: Exception) = showEmptyState()
            override fun onLoaded(profiles: List<SettingsProfile>) = showProfiles(profiles)
        })
    }

    private fun updateDropdownAdapter() {
        connectionsAdapter.clear()
        connectionsAdapter.addAll(connections.map { it.alias })
        connectionsAdapter.notifyDataSetChanged()
    }

    private fun clearConnectionFields() {
        etConnectionUrl.text?.clear()
        etConnectionKey.text?.clear()
        etConnectionAlias.text?.clear()
    }

    private fun showEmptyState() {
        btnAddCenter.visibility    = View.VISIBLE
        scrollContainer.visibility = View.GONE
    }

    private fun showProfiles(profiles: List<SettingsProfile>) {
        btnAddCenter.visibility = View.GONE
        profileListContainer.removeAllViews()
        profileListContainer.visibility = View.VISIBLE
        scrollContainer.visibility = View.VISIBLE

        // Colores
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
            container.addView(TextView(requireContext()).apply {
                text = "RTMP Connections: ${profile.connectionsCount}"
                setPadding(6, 0, 0, resources.getDimensionPixelSize(R.dimen.padding_medium))
            })

            val buttonLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 3f
                visibility = View.GONE
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
                    backgroundTintList = ContextCompat.getColorStateList(
                        requireContext(),
                        R.color.secondary_button
                    )
                    setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                }

                when (text) {
                    "Delete" -> {
                        btn.setOnClickListener {
                            repo.deleteProfile(profile.id, object : SettingsProfileRepository.DeleteCallback {
                                override fun onSuccess() {
                                    profileListContainer.removeView(card)
                                }
                                override fun onFailure(e: Exception) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Error al borrar perfil: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                        }
                    }
                    "Load" -> {
                        btn.setOnClickListener {
                            // TODO: tu lógica de carga si la necesitas
                        }
                    }
                    "Edit" -> {
                        // dentro de showProfiles(), en el case "Edit"…
                        btn.setOnClickListener {
                            // 1) Guardamos el ID que vamos a editar
                            editingProfileId = profile.id
                            // 2) Cambiamos el texto del botón final
                            btnCreateProfile.text = "Update Profile"
                            // 3) Mostramos el formulario vacío
                            showForm()
                            // 4) Llamamos a Firestore para rellenar cada campo
                            repo.fetchProfileById(profile.id) { data, error ->
                                if (error != null || data == null) {
                                    Toast.makeText(requireContext(),
                                        "Error cargando perfil para editar",
                                        Toast.LENGTH_SHORT).show()
                                    return@fetchProfileById
                                }
                                // Profile Name
                                etProfileAlias.setText(data["alias"] as? String ?: "")

                                // Conexiones
                                val conns = (data["connections"] as? List<Map<String, Any>>)?.map {
                                    StreamConnection(
                                        url       = it["rtmp_url"] as String,
                                        streamKey = it["streamkey"] as String,
                                        alias     = it["alias"] as String
                                    )
                                } ?: emptyList()
                                connections.clear()
                                connections.addAll(conns)
                                updateDropdownAdapter()
                                tilConnectionDropdown.visibility = if (conns.isNotEmpty()) View.VISIBLE else View.GONE
                                if (conns.isNotEmpty()) autoCompleteConnections.setText(conns[0].alias, false)

                                // Video Settings
                                val video = data["videoSettings"] as? Map<String, Any> ?: emptyMap()
                                autoCompleteVideoSource.setText(video["source"] as? String ?: "", false)
                                autoCompleteCodec.setText(video["codec"] as? String ?: "", false)
                                autoCompleteResolution.setText(video["resolution"] as? String ?: "", false)
                                autoCompleteFps.setText((video["fps"] as? Number)?.toString() ?: "", false)
                                etVideoBitrate.setText((video["bitrateMbps"] as? Number)?.toString() ?: "")

                                // Record Settings
                                val record = data["recordSettings"] as? Map<String, Any> ?: emptyMap()
                                autoCompleteRecordResolution.setText(record["resolution"] as? String ?: "", false)
                                etRecordBitrate.setText((record["bitrateMbps"] as? Number)?.toString() ?: "")

                                // Audio Settings
                                val audio = data["audioSettings"] as? Map<String, Any> ?: emptyMap()
                                autoCompleteAudioSource.setText(audio["source"] as? String ?: "", false)
                                autoCompleteAudioBitrate.setText((audio["bitrateKbps"] as? Number)?.toString() ?: "", false)
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
                        btnRow.visibility = View.VISIBLE
                    } else {
                        cv.setCardBackgroundColor(defaultColor)
                        btnRow.visibility = View.GONE
                    }
                }
            }
        }

        btnAddBelow.visibility = View.VISIBLE
    }



    private fun showForm() {
        resetForm()

        btnAddCenter.visibility         = View.GONE
        btnAddBelow.visibility          = View.GONE
        scrollContainer.visibility      = View.VISIBLE
        profileForm.visibility          = View.VISIBLE
        profileListContainer.visibility = View.GONE
    }

    private fun resetForm() {
        // Limpiar Profile Name
        etProfileAlias.text?.clear()
        isAliasValid = false
        btnCreateProfile.alpha = 0.5f

        // Limpiar conexiones
        connections.clear()
        updateDropdownAdapter()
        tilConnectionDropdown.visibility = View.GONE
        btnUpdateConnection.visibility = View.GONE
        btnDeleteConnection.visibility = View.GONE
        clearConnectionFields()

        // Reset Video Settings
        autoCompleteVideoSource.setText("Device Camera", false)
        autoCompleteResolution.setText("1080p", false)
        autoCompleteRecordResolution.setText("1080p", false)
        autoCompleteFps.setText("30", false)
        etVideoBitrate.setText("5")

        // Reset Record Settings
        autoCompleteRecordResolution.setText("1080p", false)
        etRecordBitrate.setText("5")

        // Reset Audio Settings
        autoCompleteAudioSource.setText("Device Audio", false)
        autoCompleteAudioBitrate.setText("160", false)
    }
}

