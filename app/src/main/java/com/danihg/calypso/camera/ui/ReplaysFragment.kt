package com.danihg.calypso.camera.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danihg.calypso.R
import com.danihg.calypso.utils.storage.StorageUtils
import com.google.android.material.button.MaterialButton
import java.io.File

class ReplaysFragment : Fragment(R.layout.fragment_replays) {

    private lateinit var recycler: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Oculta overlays
        requireActivity().findViewById<FrameLayout>(R.id.overlays_container)
            .visibility = View.GONE

        // 2) Cerrar
        view.findViewById<MaterialButton>(R.id.btnCloseReplaysMenu)
            .setOnClickListener { parentFragmentManager.popBackStack() }

        // 3) RecyclerView a 2 columnas
        recycler = view.findViewById(R.id.recyclerClips)
        recycler.setHasFixedSize(true)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)

        loadClips()
    }

    private fun loadClips() {
        val dir = StorageUtils.getTempRecordFile().parentFile ?: return
        val sid = StorageUtils.currentSessionId ?: return

        val clips = dir.listFiles { file ->
            file.isFile &&
            file.name.startsWith("${sid}_") &&
            file.name.endsWith("_rep.mp4")
        }?.sortedBy { it.lastModified() } ?: emptyList()

        android.util.Log.d("ReplaysFragment", "Clips encontrados: ${clips.map { it.name }}")

        val items = clips.mapNotNull { file ->
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(file.absolutePath)
            }
            val bmp = retriever.getFrameAtTime(500_000)
            retriever.release()
            bmp?.let { ClipItem(file, it) }
        }

        recycler.adapter = ClipsAdapter(items) { clip ->
            // TODO: play clip.file
        }
    }
}

data class ClipItem(val file: File, val thumbnail: Bitmap)
