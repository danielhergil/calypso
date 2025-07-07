package com.danihg.calypso.camera.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.danihg.calypso.R
import com.google.android.material.card.MaterialCardView

class ClipsAdapter(
    private val items: List<ClipItem>,
    private val onSelectionChanged: (selected: Set<ClipItem>) -> Unit
) : RecyclerView.Adapter<ClipsAdapter.VH>() {

    private val selected = mutableSetOf<ClipItem>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img  = view.findViewById<ImageView>(R.id.ivThumbnail)
        val txt  = view.findViewById<TextView>(R.id.tvLabel)
        val card = view as MaterialCardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clip_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        holder.img.setImageBitmap(item.thumbnail)
        holder.txt.text = item.file.nameWithoutExtension.substringAfter("_")

        // ————— Visualizar el “stroke” si está seleccionado —————
        if (selected.contains(item)) {
            holder.card.strokeWidth = 6                 // grosor del borde
            holder.card.strokeColor =                 // color del borde
                ContextCompat.getColor(holder.card.context, R.color.calypso_red)
        } else {
            holder.card.strokeWidth = 0
        }

        holder.card.setOnClickListener {
            if (selected.remove(item)) {
                // ya no seleccionado
            } else {
                selected.add(item)
            }
            notifyItemChanged(pos)
            onSelectionChanged(selected)
        }
    }

    override fun getItemCount() = items.size

    fun selectAll() {
        selected.clear()
        selected.addAll(items)
        notifyDataSetChanged()
        onSelectionChanged(selected)
    }

    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged(selected)
    }
    /** Devuelve la selección actual */
    fun getSelectedItems(): Set<ClipItem> = selected.toSet()
}
