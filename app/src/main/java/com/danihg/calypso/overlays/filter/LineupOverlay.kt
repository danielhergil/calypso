package com.danihg.calypso.overlays.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import com.danihg.calypso.camera.models.Player
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender

/**
 * Generador para overlay de Lineup:
 * - imgTeam1/imgTeam2 como fondo de zonas.
 * - logo1/logo2 y nombres centrados en cada zona.
 * - celdas de jugadores (gridCell1/gridCell2) debajo de las zonas,
 *   con gridCell1 alineado al final de imgTeam1 y gridCell2 mostrando número y nombre correctamente alineados.
 */
object LineupOverlayGenerator {

    fun createCompositeBitmap(
        imgTeam1: Bitmap?,
        imgTeam2: Bitmap?,
        logo1: Bitmap?,
        logo2: Bitmap?,
        teamName1: String,
        teamName2: String,
        gridCell1: Bitmap?,
        gridCell2: Bitmap?,
        players1: List<Player>,
        players2: List<Player>
    ): Bitmap {
        // Zona principal
        val w1 = imgTeam1?.width  ?: 0
        val h1 = imgTeam1?.height ?: 0
        val w2 = imgTeam2?.width  ?: 0
        val h2 = imgTeam2?.height ?: 0
        val spacing = 30f
        val baseH = maxOf(h1, h2).toFloat()
        val totalW = w1 + spacing + w2

        // Filas de jugadores
        val cellH1 = gridCell1?.height?.toFloat() ?: 0f
        val cellW1 = gridCell1?.width?.toFloat()  ?: 0f
        val cellH2 = gridCell2?.height?.toFloat() ?: 0f
        val cellW2 = gridCell2?.width?.toFloat()  ?: 0f
        val cellH  = maxOf(cellH1, cellH2)
        val rowSpacing = 10f
        val rows = maxOf(players1.size, players2.size)
        val cellsH = rows * cellH + (rows - 1) * rowSpacing
        val totalH = baseH + cellsH

        val bmp = Bitmap.createBitmap(totalW.toInt(), totalH.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1) Dibujar zona fondo
        val zoneTop = (baseH - h1) / 2f
        imgTeam1?.let { canvas.drawBitmap(it, 0f, zoneTop, paint) }
        imgTeam2?.let {
            val x2 = w1 + spacing
            canvas.drawBitmap(it, x2, (baseH - h2) / 2f, paint)
        }

        // 2) Encabezado: logos y nombres
        val logoHScaled = baseH * 0.8f
        val logoMargin  = 10f
        // Logo izquierdo
        logo1?.let {
            val scale = logoHScaled / it.height
            val logoW = it.width * scale
            val top    = (baseH - logoHScaled) / 2f
            val centerX = logoMargin + logoHScaled / 2f
            val left    = centerX - logoW / 2f
            canvas.drawBitmap(it.scale(logoW.toInt(), logoHScaled.toInt()), left, top, paint)
        }
        // Logo derecho
        logo2?.let {
            val scale = logoHScaled / it.height
            val logoW = it.width * scale
            val top    = (baseH - logoHScaled) / 2f
            val centerX = totalW - (logoMargin + logoHScaled / 2f)
            val left    = centerX - logoW / 2f
            canvas.drawBitmap(it.scale(logoW.toInt(), logoHScaled.toInt()), left, top, paint)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = baseH * 0.7f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(
            teamName1.uppercase(),
            w1/2f,
            baseH/2f - (textPaint.descent()+textPaint.ascent())/2f,
            textPaint
        )
        canvas.drawText(
            teamName2.uppercase(),
            w1 + spacing + w2/2f,
            baseH/2f - (textPaint.descent()+textPaint.ascent())/2f,
            textPaint
        )

        // 3) Filas de jugadores
        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = cellH * 0.6f
            isFakeBoldText = true
        }

        for (i in 0 until rows) {
            val yRow = baseH + i * (cellH + rowSpacing)

            // Celda1: alineada al final de imgTeam1
            gridCell1?.let {
                val xCell1 = w1 - cellW1
                canvas.drawBitmap(it, xCell1, yRow, paint)
                players1.getOrNull(i)?.let { p ->
                    val yText = yRow + cellH/2f - (playerPaint.descent()+playerPaint.ascent())/2f
                    playerPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(p.name, xCell1 + 10f, yText, playerPaint)
                    playerPaint.textAlign = Paint.Align.RIGHT
                    var marginX = 30f
                    if (p.number > 9) {
                        marginX = 20f
                    }
                    canvas.drawText(p.number.toString(), xCell1 + cellW1 - marginX, yText, playerPaint)
                }
            }

            // Celda2: número y nombre correctamente alineados
            gridCell2?.let {
                val xCell2      = w1 + spacing
                val cellHLocal  = it.height.toFloat()
                val cellWLocal  = it.width.toFloat()
                canvas.drawBitmap(it, xCell2, yRow, paint)

                players2.getOrNull(i)?.let { p ->
                    val yText = yRow + cellHLocal/2f - (playerPaint.descent()+playerPaint.ascent())/2f

                    // Número centrado en la franja azul
                    playerPaint.textAlign = Paint.Align.CENTER
                    val numberX = xCell2 + cellHLocal/2f + 5f
                    canvas.drawText(p.number.toString(), numberX, yText, playerPaint)

                    // Nombre alineado a la derecha del grid (se rellena hacia la izquierda)
                    playerPaint.textAlign = Paint.Align.RIGHT
                    val nameX = xCell2 + cellWLocal - 10f
                    canvas.drawText(p.name, nameX, yText, playerPaint)
                }
            }
        }

        return bmp
    }

    fun updateOverlay(
        imgTeam1: Bitmap?,
        imgTeam2: Bitmap?,
        logo1: Bitmap?,
        logo2: Bitmap?,
        teamName1: String,
        teamName2: String,
        gridCell1: Bitmap?,
        gridCell2: Bitmap?,
        players1: List<Player>,
        players2: List<Player>,
        filter: ImageObjectFilterRender
    ) {
        Handler(Looper.getMainLooper()).post {
            val comp = createCompositeBitmap(
                imgTeam1, imgTeam2,
                logo1, logo2,
                teamName1, teamName2,
                gridCell1, gridCell2,
                players1, players2
            )
            filter.setImage(comp)
        }
    }
}
