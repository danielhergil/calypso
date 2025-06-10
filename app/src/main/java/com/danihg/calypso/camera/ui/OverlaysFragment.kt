package com.danihg.calypso.camera.ui

import ScoreboardOverlayGenerator
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.camera.models.OverlaysSettingsViewModel
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
    private lateinit var btnOverlaysMenu:      MaterialButton
    private lateinit var btnInc1:              MaterialButton
    private lateinit var btnDec1:              MaterialButton
    private lateinit var btnInc2:              MaterialButton
    private lateinit var btnDec2:              MaterialButton
    private lateinit var spinnerScoreboard: ProgressBar


    private val scoreboardFilter by lazy { ImageObjectFilterRender() }
    private var logo1Bmp:    Bitmap? = null
    private var logo2Bmp:    Bitmap? = null
    private var snapshotBmp: Bitmap? = null

    // flags & counters
    private var isScoreboardAttached = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnScoreboardOverlay = view.findViewById(R.id.btnScoreboardOverlay)
        btnOverlaysMenu      = view.findViewById(R.id.btnOverlaysMenu)
        btnInc1              = view.findViewById(R.id.btn_inc_team1)
        btnDec1              = view.findViewById(R.id.btn_dec_team1)
        btnInc2              = view.findViewById(R.id.btn_inc_team2)
        btnDec2              = view.findViewById(R.id.btn_dec_team2)
        spinnerScoreboard    = view.findViewById(R.id.spinnerScoreboard)


        val root = requireActivity().findViewById<FrameLayout>(R.id.overlays_container)
        val scoreContainer = view.findViewById<LinearLayout>(R.id.score_buttons_container)

        root.post {
            val parentH = root.height
            val percentFromBottom = 0.15f
            val bottomMarginPx = (parentH * percentFromBottom).toInt()

            val lp = scoreContainer.layoutParams as FrameLayout.LayoutParams
            lp.setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin, bottomMarginPx)
            scoreContainer.layoutParams = lp
        }

        // 1) Mostrar/ocultar toggle según haya scoreboard configurado
        vm.selectedScoreboard.observe(viewLifecycleOwner) { name ->
            val ok = name.isNotBlank()
            btnScoreboardOverlay.visibility = if (ok) View.VISIBLE else View.GONE
            if (!ok) vm.setScoreboardEnabled(false)
        }

        // 2) Estado checked + aplicar/quitar filtro
        vm.scoreboardEnabled.observe(viewLifecycleOwner) { enabled ->
            btnScoreboardOverlay.isChecked = enabled
            val bgColor = if (enabled)
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            else
                Color.TRANSPARENT
            btnScoreboardOverlay.backgroundTintList = ColorStateList.valueOf(bgColor)
            val iconColor = if (enabled) Color.BLACK
            else ContextCompat.getColor(requireContext(), R.color.white)
            btnScoreboardOverlay.iconTint = ColorStateList.valueOf(iconColor)

            btnInc1.visibility = if (enabled) View.VISIBLE else View.GONE
            btnDec1.visibility = if (enabled) View.VISIBLE else View.GONE
            btnInc2.visibility = if (enabled) View.VISIBLE else View.GONE
            btnDec2.visibility = if (enabled) View.VISIBLE else View.GONE

            if (enabled) {
                if (snapshotBmp != null) reattachFilter()
                else attachSnapshotOverlay()
            } else {
                if (isScoreboardAttached) {
                    genericStream.getGlInterface().removeFilter(scoreboardFilter)
                    isScoreboardAttached = false
                }
            }
        }

//        vm.showLogos.observe(viewLifecycleOwner) { _ ->
//            if (vm.scoreboardEnabled.value == true) {
//                // 1) Limpiamos el estado para forzar descarga
//                snapshotBmp = null
//                if (isScoreboardAttached) {
//                    genericStream.getGlInterface().removeFilter(scoreboardFilter)
//                    isScoreboardAttached = false
//                }
//                // 2) Y arrancamos de nuevo la descarga/renderizado
//                attachSnapshotOverlay()
//            }
//        }

        // 3) Toggle al click
        btnScoreboardOverlay.setOnClickListener {
            // Desactiva el botón y muestra el spinner
            btnScoreboardOverlay.isEnabled = false
            btnScoreboardOverlay.icon = null
            btnScoreboardOverlay.visibility = View.GONE
            spinnerScoreboard.visibility = View.VISIBLE

            // Cambia el estado en el ViewModel
            vm.setScoreboardEnabled(!(vm.scoreboardEnabled.value ?: false))

            // Espera 2 segundos antes de restaurar el botón
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(2000) // 2000 ms = 2 segundos

                // Restaurar estado del botón
                btnScoreboardOverlay.isEnabled = true
                btnScoreboardOverlay.icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_scoreboard_overlay
                )
                spinnerScoreboard.visibility = View.GONE
                btnScoreboardOverlay.visibility = View.VISIBLE
            }
        }

        // 4) Abrir settings
        btnOverlaysMenu.setOnClickListener {
            snapshotBmp = null
            parentFragmentManager.beginTransaction()
                .replace(R.id.overlays_container, OverlaysSettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        // 5) Incremento/decremento de goles
        btnInc1.setOnClickListener {
            val nuevo = (vm.score1.value ?: 0) + 1
            vm.setScore1(nuevo)
            if (vm.scoreboardEnabled.value == true) reattachFilter()
        }
        btnDec1.setOnClickListener {
            val actual = vm.score1.value ?: 0
            if (actual > 0) {
                vm.setScore1(actual - 1)
                if (vm.scoreboardEnabled.value == true) reattachFilter()
            }
        }
        btnInc2.setOnClickListener {
            val nuevo = (vm.score2.value ?: 0) + 1
            vm.setScore2(nuevo)
            if (vm.scoreboardEnabled.value == true) reattachFilter()
        }
        btnDec2.setOnClickListener {
            val actual = vm.score2.value ?: 0
            if (actual > 0) {
                vm.setScore2(actual - 1)
                if (vm.scoreboardEnabled.value == true) reattachFilter()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.scoreboardEnabled.value == true && snapshotBmp != null) {
            isScoreboardAttached = false
            reattachFilter()
        }
    }

    private fun reattachFilter() {
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
            score1   = vm.score1.value ?: 0,
            score2   = vm.score2.value ?: 0,
            filter   = scoreboardFilter,
            showLogos = vm.showLogos.value ?: true
        )
        applyScaleAndPosition(snapshotBmp!!)
        if (!isScoreboardAttached) {
            genericStream.getGlInterface().addFilter(scoreboardFilter)
            isScoreboardAttached = true
        }
    }

    private fun attachSnapshotOverlay() {
        lifecycleScope.launch {
            val item = vm.scoreboards.value
                ?.firstOrNull { it.name == vm.selectedScoreboard.value }
            val key = if (vm.showLogos.value == true) "full" else "no_logo"
            val url = item?.snapshots?.get(key).orEmpty()
            if (url.isBlank()) return@launch

            // 1) Descarga snapshot
            val bmpSnapshot = withContext(Dispatchers.IO) {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }
            snapshotBmp = bmpSnapshot

            // 2) Primer render sin logos
            ScoreboardOverlayGenerator.updateOverlay(
                snapshot = bmpSnapshot,
                logo1    = null,
                logo2    = null,
                alias1   = "",
                alias2   = "",
                score1   = vm.score1.value ?: 0,
                score2   = vm.score2.value ?: 0,
                filter   = scoreboardFilter,
                showLogos = vm.showLogos.value ?: true
            )
            applyScaleAndPosition(bmpSnapshot)
            genericStream.getGlInterface().addFilter(scoreboardFilter)
            isScoreboardAttached = true

            // 3) Descarga logos en paralelo
            val team1 = vm.teams.value
                ?.firstOrNull { it.name == vm.selectedTeam1.value }
            val team2 = vm.teams.value
                ?.firstOrNull { it.name == vm.selectedTeam2.value }
            val d1 = async(Dispatchers.IO) {
                team1?.logoUrl?.let {
                    URL(it).openStream().use { s -> BitmapFactory.decodeStream(s) }
                }
            }
            val d2 = async(Dispatchers.IO) {
                team2?.logoUrl?.let {
                    URL(it).openStream().use { s -> BitmapFactory.decodeStream(s) }
                }
            }
            logo1Bmp = d1.await()
            logo2Bmp = d2.await()

            // 4) Re-render con logos, aliases y scores
            ScoreboardOverlayGenerator.updateOverlay(
                snapshot = bmpSnapshot,
                logo1    = logo1Bmp,
                logo2    = logo2Bmp,
                alias1   = team1?.alias.orEmpty(),
                alias2   = team2?.alias.orEmpty(),
                score1   = vm.score1.value ?: 0,
                score2   = vm.score2.value ?: 0,
                filter   = scoreboardFilter,
                showLogos = vm.showLogos.value ?: true
            )
        }
    }

    private fun applyScaleAndPosition(bmp: Bitmap) {
        val aspect = bmp.height.toFloat() / bmp.width
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()
        val isLandscape = resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

        Log.d("OverlaysFragment", "screenH: $screenH")
        Log.d("OverlaysFragment", "screenW: $screenW")
        val (sx, sy, px, py) = if (isLandscape) {
            val sxL = 0.25f * 100f
            val syL = sxL * aspect * 2f
            if (screenW > 2069) {
                Quad(sxL, syL, 3f, 5f)
            } else {
                Quad(sxL, syL, 5f, 5f)
            }

        } else {
            val sxP = 0.5f * 100f
            val syP = sxP * aspect * 0.6f
            if (screenH > 2069) {
                Quad(sxP, syP, 25f, 92f)
            } else {
                Quad(sxP, syP, 25f, 90f)
            }
        }
        scoreboardFilter.setScale(sx, sy)
        scoreboardFilter.setPosition(px, py)
    }

    private data class Quad<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )
}
