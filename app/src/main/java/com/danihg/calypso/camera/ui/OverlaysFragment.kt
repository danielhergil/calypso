package com.danihg.calypso.camera.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.camera.models.OverlaysSettingsViewModel
import com.danihg.calypso.overlays.filter.ScoreboardOverlayGenerator
import com.google.android.material.button.MaterialButton
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL


class OverlaysFragment : Fragment(R.layout.fragment_overlays) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val vm: OverlaysSettingsViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnScoreboardOverlay: MaterialButton
    private lateinit var btnOverlaysMenu: MaterialButton

    // 1) Nuestro filtro GL que mostrará el PNG descargado
    private val scoreboardFilter by lazy { ImageObjectFilterRender() }

    // 2) Aquí guardamos el Bitmap descargado (full / no_logo)
    private var snapshotBmp: Bitmap? = null

    private var isScoreboardAttached = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnScoreboardOverlay = view.findViewById(R.id.btnScoreboardOverlay)
        btnOverlaysMenu      = view.findViewById(R.id.btnOverlaysMenu)

        // 1) Show/hide based on having a configured scoreboard
        vm.selectedScoreboard.observe(viewLifecycleOwner) { name ->
            val configured = name.isNotBlank()
            btnScoreboardOverlay.visibility = if (configured) View.VISIBLE else View.GONE
            if (!configured) {
                // reset active if user cleared config
                vm.setScoreboardEnabled(false)
            }
        }

        // 2) Paint checked / tint & attach/detach filter
        vm.scoreboardEnabled.observe(viewLifecycleOwner) { enabled ->
            // 1) Visibilidad: ya controlas que solo se muestre si hay scoreboard configurado

            // 2) Checked state (para isChecked si lo necesitas)
            btnScoreboardOverlay.isChecked = enabled

            // 3) Fondo calypso_red cuando está activo; transparente cuando no
            val bgColor = if (enabled)
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            else
                Color.TRANSPARENT
            btnScoreboardOverlay.backgroundTintList = ColorStateList.valueOf(bgColor)

            // 4) Icono negro cuando está activo; blanco cuando no
            val iconColor = if (enabled)
                Color.BLACK
            else
                ContextCompat.getColor(requireContext(), R.color.white)
            btnScoreboardOverlay.iconTint = ColorStateList.valueOf(iconColor)
            // add/remove filter
            if (enabled){
                if (!isScoreboardAttached) attachSnapshotOverlay()
            } else
                if (isScoreboardAttached) {
                    genericStream.getGlInterface().removeFilter(scoreboardFilter)
                    isScoreboardAttached = false
                }
//            if (enabled) attachScoreboardFilter() else detachScoreboardFilter()
        }

        // 3) Toggle on/off when click
        btnScoreboardOverlay.setOnClickListener {
            val next = !(vm.scoreboardEnabled.value ?: false)
            vm.setScoreboardEnabled(next)
            Log.d("OverlaysFragment", "Scoreboard toggle → $next")
        }

        // 4) Open settings
        btnOverlaysMenu.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.overlays_container, OverlaysSettingsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // si regresamos del background, y estaba activo y descargado, volvemos a attach sólo si no lo está
        if (vm.scoreboardEnabled.value == true
            && snapshotBmp != null
            && !isScoreboardAttached
        ) {
            try {
                ScoreboardOverlayGenerator.updateOverlay(snapshotBmp, scoreboardFilter)
                genericStream.getGlInterface().addFilter(scoreboardFilter)
                isScoreboardAttached = true
            } catch (_: Exception) { /* ignora */ }
        }
    }

    private fun attachSnapshotOverlay() {
        // 1) Obtén el objeto ScoreboardItem según selección
        val item = vm.scoreboards.value
            ?.firstOrNull { it.name == vm.selectedScoreboard.value }
        val key = if (vm.showLogos.value == true) "full" else "no_logo"
        val url = item?.snapshots?.get(key).orEmpty()
        if (url.isBlank()) return

        // 2) Descarga en background y actualiza filtro
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                URL(url).openStream().use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    withContext(Dispatchers.Main) {
                        snapshotBmp = bmp
                        ScoreboardOverlayGenerator.updateOverlay(snapshotBmp, scoreboardFilter)

                        val widthPct = 0.5f
                        val scaleX = widthPct * 100f
                        val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                        val scaleY = scaleX * aspect * 0.6f
                        val posX = 25f
                        val posY = 90f
                        Log.d("OverlaysFragment", "scaleX: $scaleX, scaleY: $scaleY")
                        scoreboardFilter.setScale(scaleX, scaleY)
                        scoreboardFilter.setPosition(posX, posY)
                        genericStream.getGlInterface().addFilter(scoreboardFilter)
                        isScoreboardAttached = true
                    }
                }
            } catch (e: Exception) {
                Log.e("OverlaysFragment", "Error descargando snapshot", e)
            }
        }
    }
}

