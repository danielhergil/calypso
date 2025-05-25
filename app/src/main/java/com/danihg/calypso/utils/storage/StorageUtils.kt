package com.danihg.calypso.utils.storage

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object StorageUtils {

    private const val CALYPSO_DIR_NAME = "Calypso"
    private const val TEMP_RECORD_FILENAME = "temp_record.mp4"

    /**
     * Returns the `File` for the Movies/Calypso folder, creating it if necessary.
     */
    private fun getCalypsoDirectory(): File {
        val movies = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val calypso = File(movies, CALYPSO_DIR_NAME)
        if (!calypso.exists()) {
            calypso.mkdirs()
        }
        return calypso
    }

    /**
     * Generates a session ID string in the form "yyyyMMddHHmmss".
     */
    fun generateSessionId(): String {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Returns the temp record file in the Calypso directory:
     *   /Movies/Calypso/temp_record.mp4
     */
    fun getTempRecordFile(): File {
        return File(getCalypsoDirectory(), TEMP_RECORD_FILENAME)
    }

    /**
     * Renames the temp record file to a final name:
     *   <sessionId>_<stopHHmmss>.mp4
     * Returns the new File, or null if rename failed.
     */
    fun renameTempToFinal(sessionId: String): File? {
        val dir = getCalypsoDirectory()
        val temp = File(dir, TEMP_RECORD_FILENAME)
        if (!temp.exists()) return null

        // build stop timestamp
        val stopStamp = SimpleDateFormat("HHmmss", Locale.getDefault())
            .format(Date())

        val finalName = "${sessionId}_${stopStamp}.mp4"
        val finalFile = File(dir, finalName)

        return if (temp.renameTo(finalFile)) finalFile else null
    }
}