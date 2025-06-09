package com.danihg.calypso.overlays.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

object ScoreboardOverlayGenerator {

    /**
     * Dibuja el snapshot (full o no_logo) sobre un bitmap transparente
     * con las mismas dimensiones que el original, para luego pasarlo al filter.
     */
    fun createSnapshotBitmap(
        snapshot: Bitmap
    ): Bitmap {
        // creamos un bitmap de las mismas dimensiones que el snapshot
        val bmp = Bitmap.createBitmap(snapshot.width, snapshot.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // lo pintamos a tamaño completo
        canvas.drawBitmap(snapshot, 0f, 0f, null)
        return bmp
    }

    /**
     * Actualiza el filtro con el snapshot proporcionado.
     * Se lanza en el hilo principal para poder invocar setImage().
     */
    fun updateOverlay(
        snapshot: Bitmap?,
        filter: ImageObjectFilterRender
    ) {
        Handler(Looper.getMainLooper()).post {
            if (snapshot != null) {
                // 1) generamos el bitmap “listo para el filter”
                val bmp = createSnapshotBitmap(snapshot)
                // 2) lo pasamos al shader
                filter.setImage(bmp)
            } else {
                // si no hay snapshot, limpiamos la textura
                filter.release()   // elimina cualquier textura anterior
            }
            // (Opcional) ajusta escala / posición aquí si quieres un tamaño o posición distinta:
            //   filter.setScale( <factorX>, <factorY> )
            //   filter.setPosition( <xNorm>, <yNorm> )
        }
    }

}