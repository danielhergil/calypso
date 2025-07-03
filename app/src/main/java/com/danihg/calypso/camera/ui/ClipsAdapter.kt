package com.danihg.calypso.camera.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.danihg.calypso.R
import com.danihg.calypso.utils.storage.StorageUtils

class ClipsAdapter(
    private val items: List<ClipItem>,
    private val onClick: (ClipItem) -> Unit
) : RecyclerView.Adapter<ClipsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.ivThumbnail)
        val txt: TextView  = view.findViewById(R.id.tvLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clip_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.img.setImageBitmap(item.thumbnail)
        holder.txt.text = item.file.nameWithoutExtension
            .substringAfter("${StorageUtils.currentSessionId}_")
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
