
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

object ScoreboardOverlayGenerator {

    /**
     * Dibuja el snapshot, los logos de los dos equipos y sus alias
     * sobre un bitmap transparente del tamaño del snapshot.
     *
     * @param snapshot PNG principal (full o no_logo).
     * @param logo1    Logo equipo 1, se coloca a la izquierda.
     * @param logo2    Logo equipo 2, se coloca a la derecha.
     * @param alias1   Texto alias equipo 1, se coloca a la derecha de logo1.
     * @param alias2   Texto alias equipo 2, se coloca a la izquierda de logo2.
     * @param showLogos Si es false, no se muestran los logos (solo alias y scores)
     */
    fun createCompositeBitmap(
        snapshot: Bitmap,
        logo1: Bitmap?,
        logo2: Bitmap?,
        alias1: String,
        alias2: String,
        score1: Int,
        score2: Int,
        showLogos: Boolean = true
    ): Bitmap {
        val w = snapshot.width
        val h = snapshot.height
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (showLogos) {
            // 1) pintamos el snapshot completo
            canvas.drawBitmap(snapshot, 0f, 0f, null)
        } else {
            // 1) reescalamos el snapshot al 90% de su altura
            val scaleFactor = 0.65f
            val targetHeight = h * scaleFactor
            val aspectRatio = snapshot.width.toFloat() / snapshot.height.toFloat()
            val targetWidth = targetHeight * aspectRatio

            val scaledSnapshot = snapshot.scale(targetWidth.toInt(), targetHeight.toInt())
            val topOffset = (h - targetHeight) / 2f

            // 2) dibujamos el snapshot escalado centrado verticalmente
            canvas.drawBitmap(scaledSnapshot, (w - targetWidth) / 2f, topOffset, null)
        }

        // prepara paint para los alias
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = h * 0.5f  // 15% de la altura del snapshot
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }

        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = h * 0.5f  // 15% de la altura del snapshot
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }

        // 2) dibujamos equipo 1 (logo1, alias1 y score1)
        if (showLogos) {
            logo1?.let {
                val targetH = h * 0.8f
                val scale = targetH / it.height
                val targetW = it.width * scale
                val left   = 15f
                val top    = (h - targetH) / 2f
                val dstLogo1 = RectF(left, top, left + targetW, top + targetH)
                canvas.drawBitmap(it, null, dstLogo1, null)

                // alias1 justo a la derecha del logo
                val xAlias1 = left + targetW + 45f
                // baseLine: centrado vertical en el logo
                val yAlias1 = top + targetH/2f - (textPaint.descent()+textPaint.ascent())/2f
                canvas.drawText(alias1, xAlias1, yAlias1, textPaint)

                // score1 (rojo, negrita)
                if (score1 > 9) {
                    val scoreX = xAlias1 + scorePaint.measureText(alias1) + 60f
                    canvas.drawText(score1.toString(), scoreX, yAlias1, scorePaint)
                } else {
                    val scoreX = xAlias1 + scorePaint.measureText(alias1) + 70f
                    canvas.drawText(score1.toString(), scoreX, yAlias1, scorePaint)
                }
            }
        } else {
            // Sin logos - solo alias y score
            val xAlias1 = 85f
            val yAlias1 = h/2f - (textPaint.descent()+textPaint.ascent())/2f
            canvas.drawText(alias1, xAlias1, yAlias1, textPaint)

            // score1
            if (score1 > 9) {
                val scoreX = xAlias1 + textPaint.measureText(alias1) + 20f
                canvas.drawText(score1.toString(), scoreX, yAlias1, scorePaint)
            } else {
                val scoreX = xAlias1 + textPaint.measureText(alias1) + 30f
                canvas.drawText(score1.toString(), scoreX, yAlias1, scorePaint)
            }
        }

        // 3) dibujamos equipo 2 (logo2, alias2 y score2)
        if (showLogos) {
            logo2?.let {
                val targetH = h * 0.8f
                val scale = targetH / it.height
                val targetW = it.width * scale
                val left   = w - targetW - 15f
                val top    = (h - targetH) / 2f
                val dstLogo2 = RectF(left, top, left + targetW, top + targetH)
                canvas.drawBitmap(it, null, dstLogo2, null)

                // alias2 a la izquierda del logo2
                textPaint.textAlign = Paint.Align.RIGHT
                val xAlias2 = left - 45f
                val yAlias2 = top + targetH/2f - (textPaint.descent()+textPaint.ascent())/2f
                canvas.drawText(alias2, xAlias2, yAlias2, textPaint)

                // score2
                if (score2 > 9) {
                    val scoreX = xAlias2 - scorePaint.measureText(alias2) - 100f
                    canvas.drawText(score2.toString(), scoreX, yAlias2, scorePaint)
                } else {
                    val scoreX = xAlias2 - scorePaint.measureText(alias2) - 90f
                    canvas.drawText(score2.toString(), scoreX, yAlias2, scorePaint)
                }
            }
        } else {
            // Sin logos - solo alias y score
            textPaint.textAlign = Paint.Align.RIGHT
            val xAlias2 = w - 85f
            val yAlias2 = h/2f - (textPaint.descent()+textPaint.ascent())/2f
            canvas.drawText(alias2, xAlias2, yAlias2, textPaint)

            // score2
            if (score2 > 9) {
                val scoreX = xAlias2 - textPaint.measureText(alias2) - 57f
                canvas.drawText(score2.toString(), scoreX, yAlias2, scorePaint)
            } else {
                val scoreX = xAlias2 - textPaint.measureText(alias2) - 47f
                canvas.drawText(score2.toString(), scoreX, yAlias2, scorePaint)
            }
        }

        return bmp
    }

    /**
     * Actualiza el filtro con snapshot + logos + alias.
     */
    fun updateOverlay(
        snapshot: Bitmap?,
        logo1: Bitmap?,
        logo2: Bitmap?,
        alias1: String,
        alias2: String,
        score1: Int,
        score2: Int,
        filter: ImageObjectFilterRender,
        showLogos: Boolean = true
    ) {
        Handler(Looper.getMainLooper()).post {
            if (snapshot != null) {
                val composite = createCompositeBitmap(snapshot, logo1, logo2, alias1, alias2, score1, score2, showLogos)
                filter.setImage(composite)
            } else {
                filter.release()
            }
        }
    }
}