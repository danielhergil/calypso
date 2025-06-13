
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
        val w = snapshot.width.toFloat()
        val h = snapshot.height.toFloat()
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Si mostramos logos, primero pintamos el snapshot completo
        if (showLogos) {
            canvas.drawBitmap(snapshot, 0f, 0f, null)
        } else {
            // Escalamos snapshot al 65% de altura y centrado
            val scaleFactor = 0.65f
            val targetHeight = h * scaleFactor
            val aspectRatio = snapshot.width / snapshot.height.toFloat()
            val targetWidth = targetHeight * aspectRatio
            val topOffset = (h - targetHeight) / 2f
            canvas.drawBitmap(
                snapshot.scale(targetWidth.toInt(), targetHeight.toInt()),
                (w - targetWidth) / 2f,
                topOffset,
                null
            )
        }

        // Paints para alias y scores
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = h * 0.5f
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = h * 0.5f
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }

        if (showLogos) {
            // Constantes de layout
            val logoMargin = 15f
            val logoHeight = h * 0.8f
            val aliasOffset = 45f

            // Equipo 1
            logo1?.let {
                val scale = logoHeight / it.height
                val logoWidth = it.width * scale
                val top = (h - logoHeight) / 2f
                // Centro fijo para el logo 1
                val centerX1 = logoMargin + logoHeight / 2f
                val left1 = centerX1 - logoWidth / 2f
                val dst1 = RectF(left1, top, left1 + logoWidth, top + logoHeight)
                canvas.drawBitmap(it, null, dst1, null)

                // Alias 1 en posición fija
                val xAlias1 = logoMargin + logoHeight + aliasOffset
                val yAlias1 = top + logoHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(alias1, xAlias1, yAlias1, textPaint)

                // Score 1 al lado del alias
                val scoreOffset1 = if (score1 > 9) 60f else 70f
                val xScore1 = xAlias1 + textPaint.measureText(alias1) + scoreOffset1
                canvas.drawText(score1.toString(), xScore1, yAlias1, scorePaint)
            }

            // Equipo 2
            logo2?.let {
                val scale = logoHeight / it.height
                val logoWidth = it.width * scale
                val top = (h - logoHeight) / 2f
                // Centro fijo para el logo 2
                val centerX2 = w - logoMargin - logoHeight / 2f
                val left2 = centerX2 - logoWidth / 2f
                val dst2 = RectF(left2, top, left2 + logoWidth, top + logoHeight)
                canvas.drawBitmap(it, null, dst2, null)

                // Alias 2 en posición fija (alineado a la derecha)
                textPaint.textAlign = Paint.Align.RIGHT
                val xAlias2 = w - (logoMargin + logoHeight + aliasOffset)
                val yAlias2 = top + logoHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(alias2, xAlias2, yAlias2, textPaint)

                // Score 2 al lado del alias
                val scoreOffset2 = if (score2 > 9) 100f else 90f
                val xScore2 = xAlias2 - textPaint.measureText(alias2) - scoreOffset2
                canvas.drawText(score2.toString(), xScore2, yAlias2, scorePaint)

                // Restaurar alineación por si se vuelve a reutilizar
                textPaint.textAlign = Paint.Align.LEFT
            }
        } else {
            // Solo alias y scores cuando no hay logos
            val xAlias1 = 85f
            val yAlias1 = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(alias1, xAlias1, yAlias1, textPaint)
            val offset1 = if (score1 > 9) 20f else 30f
            canvas.drawText(score1.toString(), xAlias1 + textPaint.measureText(alias1) + offset1, yAlias1, scorePaint)

            textPaint.textAlign = Paint.Align.RIGHT
            val xAlias2 = w - 85f
            val yAlias2 = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(alias2, xAlias2, yAlias2, textPaint)
            val offset2 = if (score2 > 9) 57f else 47f
            canvas.drawText(score2.toString(), xAlias2 - textPaint.measureText(alias2) - offset2, yAlias2, scorePaint)
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
