package com.danihg.calypso.overlays.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

object FooterOverlayGenerator {

    /**
     * Crea un bitmap compuesto para el footer:
     *  1) fondo descargado de Firebase
     *  2) logos en los extremos, cuadrados y ocupando el 90% de la altura
     *  3) nombres de equipos centrados en su mitad superior
     *  4) texto de footer centrado en la mitad media
     *  5) puntuación centrada en la mitad inferior
     */
    fun createCompositeBitmap(
        base: Bitmap,
        logo1: Bitmap?,
        logo2: Bitmap?,
        teamName1: String,
        teamName2: String,
        footerText: String,
        score1: Int,
        score2: Int
    ): Bitmap {
        val w = base.width.toFloat()
        val h = base.height.toFloat()

        // Creamos el bitmap de salida
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1) Fondo
        canvas.drawBitmap(base, 0f, 0f, paint)

        // Tamaño de los logos: 90% de la altura, cuadrados
        val logoSizeF = h * 0.8f
        val logoSize = logoSizeF.toInt()
        // Y desplazamos un 5% del ancho desde el borde para que no queden cortados
        val marginX = w * 0.017f
        // Centrarlos verticalmente
        val topY = (h - logoSizeF) / 2f

        // 2) Logos pegados a los extremos
        val leftLogoLeft  = marginX
        val leftLogoRight = w - marginX - logoSizeF

        logo1?.let {
            val scaled = it.scale(logoSize, logoSize)
            canvas.drawBitmap(scaled, leftLogoLeft, topY, paint)
        }
        logo2?.let {
            val scaled = it.scale(logoSize, logoSize)
            canvas.drawBitmap(scaled, leftLogoRight, topY, paint)
        }

        // 3) Nombres de equipo (en la franja superior, 10% – 30% de la altura)
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = h * 0.3f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val nameY = h * 0.35f - (namePaint.descent() + namePaint.ascent()) / 2f
        canvas.drawText(teamName1.uppercase(), w * 0.25f, nameY, namePaint)
        canvas.drawText(teamName2.uppercase(), w * 0.75f, nameY, namePaint)

        // 4) Texto de footer (franja media, 40% – 60%)
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = h * 0.27f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val txtY = h * 0.85f - (txtPaint.descent() + txtPaint.ascent()) / 2f
        canvas.drawText(footerText.uppercase(), w / 2f, txtY, txtPaint)

        // 5) Puntuación (franja inferior, 70% – 90%)
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = h * 0.35f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val scoreY = h * 0.35f - (scorePaint.descent() + scorePaint.ascent()) / 2f
        // dibujamos cada score centrado en 40% y 60%
        canvas.drawText("%02d".format(score1), w * 0.45f, scoreY, scorePaint)
        canvas.drawText("%02d".format(score2), w * 0.55f, scoreY, scorePaint)

        return bmp
    }

    /**
     * Actualiza el filtro con el nuevo bitmap en el hilo UI.
     */
    fun updateOverlay(
        base: Bitmap,
        logo1: Bitmap?,
        logo2: Bitmap?,
        teamName1: String,
        teamName2: String,
        footerText: String,
        score1: Int,
        score2: Int,
        filter: ImageObjectFilterRender
    ) {
        Handler(Looper.getMainLooper()).post {
            filter.setImage(
                createCompositeBitmap(
                    base, logo1, logo2,
                    teamName1, teamName2,
                    footerText, score1, score2
                )
            )
        }
    }
}
