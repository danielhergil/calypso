package com.danihg.calypso.utils.storage

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageUtils {

    private const val CALYPSO_DIR_NAME = "Calypso"
    const val TEMP_RECORD_FILENAME = "temp_record.mp4"

    // Añadimos esta variable para “guardar” el sessionId actual
    // cada vez que se llame a generateSessionId().
    var currentSessionId: String? = null

    /**
     * Returns the `File` para Movies/Calypso, creándolo si no existe.
     */
    private fun getCalypsoDirectory(): File {
        val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val calypso = File(movies, CALYPSO_DIR_NAME)
        if (!calypso.exists()) {
            calypso.mkdirs()
        }
        return calypso
    }

    /**
     * Genera un session ID en el formato "yyyyMMddHHmmss" y lo guarda en currentSessionId.
     */
    fun generateSessionId(): String {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val id = sdf.format(Date())
        currentSessionId = id
        return id
    }

    /**
     * Devuelve el File temporal en /Movies/Calypso/temp_record.mp4
     */
    fun getTempRecordFile(): File {
        return File(getCalypsoDirectory(), TEMP_RECORD_FILENAME)
    }

    /**
     * Renombra el fichero temporal “temp_record.mp4” a "<sessionId>_<stopHHmmss>.mp4".
     * Devuelve el File final o null si falla.
     */
    fun renameTempToFinal(sessionId: String): File? {
        val dir = getCalypsoDirectory()
        val temp = File(dir, TEMP_RECORD_FILENAME)
        Log.d("StorageUtils", "renameTempToFinal(): buscando temp en ${temp.absolutePath}, existe? ${temp.exists()}")
        if (!temp.exists()) return null

        val stopStamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        val finalName = "${sessionId}_$stopStamp.mp4"
        val finalFile = File(dir, finalName)

        val success = temp.renameTo(finalFile)
        Log.d("StorageUtils", "renameTempToFinal(): renombrar a ${finalFile.absolutePath}, éxito? $success, final existe? ${finalFile.exists()}")

        return if (success) finalFile else null
    }

    /**
     * Recorta los últimos [durationMs] milisegundos de [inputPath] y los guarda en [outputPath].
     * Devuelve true si tuvo éxito.
     */
    fun clipLastTenSeconds(
        inputPath: String,
        outputPath: String,
        durationMs: Long = 10_000
    ): Boolean {
        // 1) Calcula duración total
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(inputPath)
        }
        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val totalMs = durStr?.toLongOrNull() ?: run {
            retriever.release()
            return false
        }
        retriever.release()

        val startUs = ((totalMs - durationMs).coerceAtLeast(0)) * 1_000

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor().apply {
                setDataSource(inputPath)
            }
            // 2) Encuentra tracks
            var vidIn = -1; var audIn = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) vidIn = i
                else if (mime.startsWith("audio/")) audIn = i
            }
            // 3) Prepara muxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vidOut = if (vidIn >= 0) muxer.addTrack(extractor.getTrackFormat(vidIn)) else -1
            val audOut = if (audIn >= 0) muxer.addTrack(extractor.getTrackFormat(audIn)) else -1
            muxer.start()

            // 4) Buffer & info
            val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            // 5) Función para copiar un track
            fun copyTrack(inIndex: Int, outIndex: Int) {
                if (inIndex < 0 || outIndex < 0) return
                extractor.selectTrack(inIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else 0
                    buffer.position(0)
                    muxer.writeSampleData(outIndex, buffer, info)
                    buffer.clear()
                    extractor.advance()
                }
                extractor.unselectTrack(inIndex)
            }

            // 6) Copia video y audio
            copyTrack(vidIn, vidOut)
            copyTrack(audIn, audOut)

            // 7) Finaliza
            muxer.stop()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            extractor?.release()
            muxer?.release()
        }
    }
}
