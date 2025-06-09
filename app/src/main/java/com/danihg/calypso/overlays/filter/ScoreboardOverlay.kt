package com.danihg.calypso.overlays.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

object ScoreboardOverlayGenerator {

    /**
     * Dibuja el snapshot y los logos de los dos equipos sobre un bitmap transparente.
     *
     * @param snapshot PNG principal (full o no_logo).
     * @param logo1    Logo equipo 1, se coloca a la izquierda.
     * @param logo2    Logo equipo 2, se coloca a la derecha.
     */
    fun createCompositeBitmap(
        snapshot: Bitmap,
        logo1: Bitmap?,
        logo2: Bitmap?
    ): Bitmap {
        // 1) Creamos un bitmap de las mismas dimensiones que el snapshot
        val w = snapshot.width
        val h = snapshot.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 2) Pintamos el snapshot de fondo
        canvas.drawBitmap(snapshot, 0f, 0f, null)

        // 3) Si hay logo1, lo dibujamos escalado proporcionalmente (por ejemplo un 20% del alto)
        logo1?.let {
            val targetH = (h * 0.8f)
            val scale = targetH / it.height
            val targetW = it.width * scale
            val left   = 15f
            val top    = (h - targetH) / 2f
            val dst1   = android.graphics.RectF(left, top, left + targetW, top + targetH)
            canvas.drawBitmap(it, null, dst1, null)
        }

        // 4) Si hay logo2, lo dibujamos a la derecha
        logo2?.let {
            val targetH = (h * 0.8f)
            val scale = targetH / it.height
            val targetW = it.width * scale
            val left   = w - targetW - 15f
            val top    = (h - targetH) / 2f
            val dst2   = android.graphics.RectF(left, top, left + targetW, top + targetH)
            canvas.drawBitmap(it, null, dst2, null)
        }

        return bmp
    }

    /**
     * Actualiza el filtro con el snapshot y los dos logos.
     * Se ejecuta en el hilo principal para poder invocar setImage().
     *
     * @param snapshot PNG principal (full o no_logo); si es null, limpia.
     * @param logo1    Logo equipo 1, o null para placeholder left.
     * @param logo2    Logo equipo 2, o null para placeholder right.
     * @param filter   El ImageObjectFilterRender a actualizar.
     */
    fun updateOverlay(
        snapshot: Bitmap?,
        logo1: Bitmap?,
        logo2: Bitmap?,
        filter: ImageObjectFilterRender
    ) {
        Handler(Looper.getMainLooper()).post {
            if (snapshot != null) {
                // 1) compone snapshot + logos
                val composite = createCompositeBitmap(snapshot, logo1, logo2)
                // 2) lo pasa al shader
                filter.setImage(composite)
            } else {
                //  si no hay snapshot, limpiamos la textura
                filter.release()
            }
        }
    }
}
