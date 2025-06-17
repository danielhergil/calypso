package com.danihg.calypso.overlays.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

object CoverOverlayGenerator {

    fun createCompositeBitmap(
        base: Bitmap,
        logo1: Bitmap?,
        logo2: Bitmap?,
        label: String,
        teamName1: String,
        teamName2: String
    ): Bitmap {
        val w = base.width.toFloat()
        val h = base.height.toFloat()

        // definimos proporciones de barras
        val barRatio = 0.15f
        val topH    = h * barRatio
        val botH    = h * barRatio
        val midY    = topH
        val midH    = h - topH - botH
        val botY    = topH + midH

        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1) fondo
        canvas.drawBitmap(base, 0f, 0f, paint)

        // 2) label en la barra superior
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = topH * 0.6f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(
            label.uppercase(),
            w/2f,
            topH * 0.6f - (labelPaint.descent() + labelPaint.ascent() - 20f) / 2f,
            labelPaint
        )

        // 3) logos en la zona central
        val logoMaxH = midH * 0.6f
        listOf(logo1 to 0.20f, logo2 to 0.80f).forEach { (logo, cxRatio) ->
            logo?.let {
                val scale = logoMaxH / it.height
                val lw    = it.width  * scale
                val lh    = it.height * scale
                val left  = w * cxRatio - lw/2f
                val top   = midY + (midH - lh)/2f
                canvas.drawBitmap(it.scale(lw.toInt(), lh.toInt()), left, top, paint)
            }
        }

        // 4) nombres de equipo en la barra inferior
        val teamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = botH * 0.7f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val teamTextY = botY + botH * 0.4f - (teamPaint.descent() + teamPaint.ascent()) / 2f
        listOf(teamName1 to 0.20f, teamName2 to 0.80f).forEach { (name, cxRatio) ->
            canvas.drawText(
                name.uppercase(),
                w * cxRatio,
                teamTextY,
                teamPaint
            )
        }

        return bmp
    }

    fun updateOverlay(
        base: Bitmap,
        logo1: Bitmap?,
        logo2: Bitmap?,
        label: String,
        teamName1: String,
        teamName2: String,
        filter: ImageObjectFilterRender
    ) {
        Handler(Looper.getMainLooper()).post {
            filter.setImage(
                createCompositeBitmap(base, logo1, logo2, label, teamName1, teamName2)
            )
        }
    }
}
