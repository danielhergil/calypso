package com.danihg.calypso.utils.ui

import android.os.Handler
import android.os.Looper
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.danihg.calypso.R
import com.google.android.material.button.MaterialButton

/**
 * Replaces btn’s icon with a small red spinner, disables it for [delayMs],
 * then restores via [onDone].
 */
fun showTemporarySpinnerOn(
    btn: MaterialButton,
    delayMs: Long,
    onDone: () -> Unit
) {
    val ctx = btn.context
    // build a red circular spinner drawable
    val spinner = CircularProgressDrawable(ctx).apply {
        setStyle(CircularProgressDrawable.DEFAULT)
        setColorSchemeColors(
            ContextCompat.getColor(ctx, R.color.calypso_red)
        )
        start()
    }
    btn.apply {
        isEnabled = false
        // replace icon with spinner
        icon = spinner
    }
    // after delayMs, stop spinner & invoke the restore block on the main thread
    Handler(Looper.getMainLooper()).postDelayed({
        spinner.stop()
        onDone()
        btn.isEnabled = true
    }, delayMs)
}