package com.danihg.calypso.camera.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
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
    private var logo1Bmp: Bitmap? = null
    private var logo2Bmp: Bitmap? = null

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
        // Si volvemos de background y estaba activo y ya tenemos snapshot, lo reaplicamos
        if (vm.scoreboardEnabled.value == true
            && snapshotBmp  != null
            && !isScoreboardAttached
        ) {
            try {
                ScoreboardOverlayGenerator.updateOverlay(
                    snapshot = snapshotBmp,
                    logo1    = logo1Bmp,
                    logo2    = logo2Bmp,
                    filter   = scoreboardFilter
                )
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
                // Descarga principal (snapshot)
                val bmpSnapshot = URL(url).openStream().use {
                    BitmapFactory.decodeStream(it)
                }
                // Descarga logo1 (usa la URL guardada en el ViewModel)
                val team1 = vm.teams.value?.firstOrNull { it.name == vm.selectedTeam1.value }
                val bmpLogo1 = team1?.logoUrl?.let { logoUrl ->
                    URL(logoUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }
                // Descarga logo2
                val team2 = vm.teams.value?.firstOrNull { it.name == vm.selectedTeam2.value }
                val bmpLogo2 = team2?.logoUrl?.let { logoUrl ->
                    URL(logoUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }

                withContext(Dispatchers.Main) {
                    snapshotBmp = bmpSnapshot
                    logo1Bmp    = bmpLogo1
                    logo2Bmp    = bmpLogo2

                    // 3) Componer overlay
                    ScoreboardOverlayGenerator.updateOverlay(
                        snapshot = snapshotBmp,
                        logo1    = logo1Bmp,
                        logo2    = logo2Bmp,
                        filter   = scoreboardFilter
                    )

                    // 4) Escala y posición según orientación
                    val aspect = bmpSnapshot.height.toFloat() / bmpSnapshot.width
                    val isLandscape = resources.configuration.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE

                    val (scaleX, scaleY, posX, posY) = if (isLandscape) {
                        val sx = 0.25f * 100f
                        val sy = sx * aspect * 2f
                        Quadruple(sx, sy, 5f, 5f)
                    } else {
                        val sx = 0.5f * 100f
                        val sy = sx * aspect * 0.6f
                        Quadruple(sx, sy, 25f, 90f)
                    }

                    scoreboardFilter.setScale(scaleX, scaleY)
                    scoreboardFilter.setPosition(posX, posY)

                    genericStream.getGlInterface().addFilter(scoreboardFilter)
                    isScoreboardAttached = true
                }
            } catch (e: Exception) {
                Log.e("OverlaysFragment", "Error descargando snapshot o logos", e)
            }
        }
    }

    private data class Quadruple<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )
}

