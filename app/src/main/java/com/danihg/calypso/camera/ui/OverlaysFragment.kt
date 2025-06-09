package com.danihg.calypso.camera.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class OverlaysFragment : Fragment(R.layout.fragment_overlays) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val vm: OverlaysSettingsViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    private lateinit var btnScoreboardOverlay: MaterialButton
    private lateinit var btnOverlaysMenu: MaterialButton

    private val scoreboardFilter by lazy { ImageObjectFilterRender() }
    private var logo1Bmp: Bitmap? = null
    private var logo2Bmp: Bitmap? = null
    private var snapshotBmp: Bitmap? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnScoreboardOverlay = view.findViewById(R.id.btnScoreboardOverlay)
        btnOverlaysMenu      = view.findViewById(R.id.btnOverlaysMenu)

        // 1) Mostrar/ocultar toggle
        vm.selectedScoreboard.observe(viewLifecycleOwner) { name ->
            val ok = name.isNotBlank()
            btnScoreboardOverlay.visibility = if (ok) View.VISIBLE else View.GONE
            if (!ok) vm.setScoreboardEnabled(false)
        }

        // 2) Estado checked + aplicar/quitar filtro
        vm.scoreboardEnabled.observe(viewLifecycleOwner) { enabled ->
            btnScoreboardOverlay.isChecked = enabled
            val bgColor = if (enabled) ContextCompat.getColor(requireContext(), R.color.calypso_red)
            else Color.TRANSPARENT
            btnScoreboardOverlay.backgroundTintList = ColorStateList.valueOf(bgColor)
            val iconColor = if (enabled) Color.BLACK else ContextCompat.getColor(requireContext(), R.color.white)
            btnScoreboardOverlay.iconTint = ColorStateList.valueOf(iconColor)

            if (enabled) {
                if (snapshotBmp != null) {
                    reattachFilter()
                } else {
                    attachSnapshotOverlay()
                }
            } else {
                genericStream.getGlInterface().removeFilter(scoreboardFilter)
            }
        }

        btnScoreboardOverlay.setOnClickListener {
            vm.setScoreboardEnabled(!(vm.scoreboardEnabled.value ?: false))
        }
        btnOverlaysMenu.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.overlays_container, OverlaysSettingsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.scoreboardEnabled.value == true && snapshotBmp != null) {
            reattachFilter()
        }
    }
    

    private fun reattachFilter() {
        // vuelve a componer y re-aplicar escala/posición
        ScoreboardOverlayGenerator.updateOverlay(
            snapshot = snapshotBmp,
            logo1    = logo1Bmp,
            logo2    = logo2Bmp,
            alias1   = vm.teams.value
                ?.firstOrNull { it.name == vm.selectedTeam1.value }
                ?.alias.orEmpty(),
            alias2   = vm.teams.value
                ?.firstOrNull { it.name == vm.selectedTeam2.value }
                ?.alias.orEmpty(),
            filter   = scoreboardFilter
        )
        applyScaleAndPosition(snapshotBmp!!)
        genericStream.getGlInterface().addFilter(scoreboardFilter)
    }

    private fun attachSnapshotOverlay() {
        lifecycleScope.launch {
            val item = vm.scoreboards.value
                ?.firstOrNull { it.name == vm.selectedScoreboard.value }
            val key = if (vm.showLogos.value == true) "full" else "no_logo"
            val url = item?.snapshots?.get(key).orEmpty()
            if (url.isBlank()) return@launch

            // 1) Descargar snapshot
            val bmpSnapshot = withContext(Dispatchers.IO) {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }
            snapshotBmp = bmpSnapshot

            // 2) Pintar snapshot inmediato (sin logos)
            ScoreboardOverlayGenerator.updateOverlay(
                snapshot = bmpSnapshot,
                logo1    = null,
                logo2    = null,
                alias1   = "",
                alias2   = "",
                filter   = scoreboardFilter
            )
            applyScaleAndPosition(bmpSnapshot)
            genericStream.getGlInterface().addFilter(scoreboardFilter)

            // 3) Descargar logos en paralelo
            val team1 = vm.teams.value
                ?.firstOrNull { it.name == vm.selectedTeam1.value }
            val team2 = vm.teams.value
                ?.firstOrNull { it.name == vm.selectedTeam2.value }
            val d1 = async(Dispatchers.IO) {
                team1?.logoUrl?.let { URL(it).openStream().use { s -> BitmapFactory.decodeStream(s) } }
            }
            val d2 = async(Dispatchers.IO) {
                team2?.logoUrl?.let { URL(it).openStream().use { s -> BitmapFactory.decodeStream(s) } }
            }
            logo1Bmp = d1.await()
            logo2Bmp = d2.await()

            // 4) Recompone con logos y aliases
            ScoreboardOverlayGenerator.updateOverlay(
                snapshot = bmpSnapshot,
                logo1    = logo1Bmp,
                logo2    = logo2Bmp,
                alias1   = team1?.alias.orEmpty(),
                alias2   = team2?.alias.orEmpty(),
                filter   = scoreboardFilter
            )
            // la textura del filtro cambia en caliente, no hay que re-addFilter
        }
    }

    private fun applyScaleAndPosition(bmp: Bitmap) {
        val aspect = bmp.height.toFloat() / bmp.width
        val isLandscape = resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        val (sx, sy, px, py) = if (isLandscape) {
            // landscape: más ancho, menos alto, arriba-izquierda
            val sxL = 0.25f * 100f
            val syL = sxL * aspect * 2f
            Quad(sxL, syL, 5f, 5f)
        } else {
            // portrait: ancho 50%, alto proporcional, abajo-centro
            val sxP = 0.5f * 100f
            val syP = sxP * aspect * 0.6f
            Quad(sxP, syP, 25f, 90f)
        }
        scoreboardFilter.setScale(sx, sy)
        scoreboardFilter.setPosition(px, py)
    }

    private data class Quad<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )
}
