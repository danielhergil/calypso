package com.danihg.calypso.utils.storage

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object StorageUtils {

    private const val CALYPSO_DIR_NAME = "Calypso"
    private const val TEMP_RECORD_FILENAME = "temp_record.mp4"

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
        if (!temp.exists()) return null

        val stopStamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        val finalName = "${sessionId}_$stopStamp.mp4"
        val finalFile = File(dir, finalName)

        return if (temp.renameTo(finalFile)) finalFile else null
    }
}
