package com.danihg.calypso.camera.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.utils.storage.StorageUtils
import com.google.android.material.button.MaterialButton
import java.io.File

class ReplaysFragment : Fragment(R.layout.fragment_replays) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var recycler: RecyclerView
    private lateinit var checkAll: CheckBox
    private lateinit var playButton: MaterialButton
    private lateinit var adapter: ClipsAdapter

    // Para evitar bucles al cambiar el checkbox
    private var ignoreCheckAll = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Oculta overlays
        requireActivity()
            .findViewById<FrameLayout>(R.id.overlays_container)
            .visibility = View.GONE

        // 2) “Select all”
        checkAll = view.findViewById(R.id.checkbox_select_all)
        checkAll.setOnCheckedChangeListener { _, checked ->
            if (ignoreCheckAll) return@setOnCheckedChangeListener
            if (checked) adapter.selectAll() else adapter.clearSelection()
        }

        // 3) Botón Cerrar
        view.findViewById<MaterialButton>(R.id.btnCloseReplaysMenu)
            .setOnClickListener {
                // primero devolvemos el overlay sin petar si no estamos attachados
                activity
                    ?.findViewById<FrameLayout>(R.id.overlays_container)
                    ?.visibility = View.VISIBLE
                // luego hacemos el pop
                parentFragmentManager.popBackStack()
            }

        // 4) RecyclerView
        recycler = view.findViewById(R.id.recyclerClips)
        recycler.setHasFixedSize(true)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)

        // 5) Botón PLAY
        playButton = view.findViewById(R.id.btnPlayClips)
        playButton.isEnabled = false
        playButton.setOnClickListener {
            val files = adapter.getSelectedItems().map { it.file }
            val fm = requireActivity().supportFragmentManager

            // 2) Primero, mostramos de nuevo el contenedor de overlays
            activity
                ?.findViewById<FrameLayout>(R.id.overlays_container)
                ?.visibility = View.VISIBLE
            // a continuación:
            val popped = fm.popBackStackImmediate()

            if (!popped) {
                Log.e("ReplaysFragment","⚠️ no había nada que poppear")
                return@setOnClickListener
            }

            // 4) Por último, invocamos tu secuencia normal de reproducción
            val sm = fm.findFragmentById(R.id.settings_container) as? SettingsFragment
            if (sm != null) {
                sm.playClipsSequentially(files)
            } else {
                Log.e("ReplaysFragment","⚠️ No encontré SettingsFragment tras el pop")
            }
        }

        // 6) carga inicial
        loadClips()
    }

    private fun loadClips() {
        val dir = StorageUtils.getTempRecordFile().parentFile ?: return
        val sid = StorageUtils.currentSessionId ?: return

        val clips = dir.listFiles { f ->
            f.isFile &&
                    f.name.startsWith("${sid}_") &&
                    f.name.endsWith("_rep.mp4")
        }?.sortedBy { it.lastModified() } ?: emptyList()

        val items = clips.mapNotNull { file ->
            val thumb = MediaMetadataRetriever().apply {
                setDataSource(file.absolutePath)
            }.use { it.getFrameAtTime(500_000) }
            thumb?.let { ClipItem(file, it) }
        }

        adapter = ClipsAdapter(items) { selected ->
            // actualiza “select all” sin disparar el listener
            ignoreCheckAll = true
            checkAll.isChecked = (selected.size == items.size)
            ignoreCheckAll = false
            // habilita PLAY si hay al menos uno
            playButton.isEnabled = selected.isNotEmpty()
        }

        recycler.adapter = adapter
    }
}

data class ClipItem(val file: File, val thumbnail: Bitmap)