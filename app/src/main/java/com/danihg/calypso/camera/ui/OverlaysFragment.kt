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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.camera.models.OverlaysSettingsViewModel
import com.danihg.calypso.overlays.filter.CoverOverlayGenerator
import com.danihg.calypso.overlays.filter.LineupOverlayGenerator
import com.google.android.material.button.MaterialButton
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

    private lateinit var btnLineupOverlay: MaterialButton
    private lateinit var spinnerLineup:     ProgressBar
    private val lineupFilter by lazy { ImageObjectFilterRender() }

    private lateinit var btnCoverOverlay:  MaterialButton
    private lateinit var spinnerCover:     ProgressBar
    private val coverFilter      by lazy { ImageObjectFilterRender() }
    private var isCoverAttached  = false
    private lateinit var frameCoverOverlay: FrameLayout
    private var coverLabel: String = ""

    private val scoreboardFilter by lazy { ImageObjectFilterRender() }
    private var logo1Bmp:    Bitmap? = null
    private var logo2Bmp:    Bitmap? = null
    private var snapshotBmp: Bitmap? = null

    // flags & counters
    private var isScoreboardAttached = false

    private var compositeLineupBmp: Bitmap? = null
    private var isLineupAttached = false

    private lateinit var submenuOverlays: LinearLayout
    private lateinit var btnOverlaysToggle: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnScoreboardOverlay = view.findViewById(R.id.btnScoreboardOverlay)
        btnOverlaysMenu      = view.findViewById(R.id.btnOverlaysMenu)
        btnInc1              = view.findViewById(R.id.btn_inc_team1)
        btnDec1              = view.findViewById(R.id.btn_dec_team1)
        btnInc2              = view.findViewById(R.id.btn_inc_team2)
        btnDec2              = view.findViewById(R.id.btn_dec_team2)
        spinnerScoreboard    = view.findViewById(R.id.spinnerScoreboard)
        submenuOverlays      = view.findViewById(R.id.overlays_submenu)
        btnOverlaysToggle    = view.findViewById(R.id.btnOverlaysToggle)
        btnLineupOverlay     = view.findViewById(R.id.btnLineupOverlay)
        spinnerLineup        = view.findViewById(R.id.spinnerLineup)
        val frameSB          = view.findViewById<FrameLayout>(R.id.frameScoreboardOverlay)
        val frameLU          = view.findViewById<FrameLayout>(R.id.frameLineupOverlay)
        btnCoverOverlay      = view.findViewById(R.id.btnCoverOverlay)
        spinnerCover         = view.findViewById(R.id.spinnerCover)
        frameCoverOverlay    = view.findViewById(R.id.frameCoverOverlay)

        submenuOverlays.visibility = View.GONE

        val root = requireActivity().findViewById<FrameLayout>(R.id.overlays_container)
        val scoreContainer = view.findViewById<LinearLayout>(R.id.score_buttons_container)

        root.post {
            val parentH = root.height.toFloat()
            val parentW = root.width.toFloat()
            val isLandscape = resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE

            // 1) margen inferior al 15%
            val bottomMarginPx = (parentH * 0.15f).toInt()

            // 2) calculamos cuánto “de más” tenemos respecto a 2069px
            val extra = parentW - 2219f

            // 3) si estamos en landscape y la pantalla es más ancha, desplazamos
            //    el contenedor la mitad del extra hacia la izquierda
            scoreContainer.translationX = if (isLandscape && extra > 0f) {
                -extra / 2f
            } else {
                0f
            }

            // 4) aplicamos sólo el margen inferior (dejamos el leftMargin original intacto)
            val lp = scoreContainer.layoutParams as FrameLayout.LayoutParams
            lp.setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin, bottomMarginPx)
            scoreContainer.layoutParams = lp
        }

        vm.selectedCoverLabel.observe(viewLifecycleOwner) { label ->
            coverLabel = label
        }

        vm.selectedCover.observe(viewLifecycleOwner) { name ->
            val has = name.isNotBlank()
            btnCoverOverlay.visibility = if (has) View.VISIBLE else View.GONE
            if (!has) vm.setCoverEnabled(false)
            updateOverlaysToggle()
        }

        vm.coverEnabled.observe(viewLifecycleOwner) { enabled ->
            btnCoverOverlay.isChecked = enabled
            val bg = if (enabled)
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            else Color.TRANSPARENT
            btnCoverOverlay.backgroundTintList = ColorStateList.valueOf(bg)
            btnCoverOverlay.iconTint =
                ColorStateList.valueOf(if (enabled) Color.BLACK else Color.WHITE)

            if (enabled) {
                attachCoverOverlay()
            } else if (isCoverAttached) {
                genericStream.getGlInterface().removeFilter(coverFilter)
                isCoverAttached = false
            }
        }

        vm.selectedLineup.observe(viewLifecycleOwner)  { name ->
            val has = name.isNotBlank()
            btnLineupOverlay.visibility = if (has) View.VISIBLE else View.GONE
            if (!has) vm.setLineupEnabled(false)
            updateOverlaysToggle()
        }

        vm.lineupEnabled.observe(viewLifecycleOwner) { enabled ->
            btnLineupOverlay.isChecked = enabled
            val bg = if (enabled)
                ContextCompat.getColor(requireContext(), R.color.calypso_red)
            else Color.TRANSPARENT
            btnLineupOverlay.backgroundTintList = ColorStateList.valueOf(bg)
            btnLineupOverlay.iconTint =
                ColorStateList.valueOf(if (enabled) Color.BLACK else Color.WHITE)

            if (enabled) {
                // si ya tenemos el composite, hacemos re‐attach, si no, attach nuevo
                if (compositeLineupBmp != null) reattachLineupFilter()
                else attachLineupOverlay()
            } else if (isLineupAttached) {
                // solo quitamos si antes lo añadimos
                genericStream.getGlInterface().removeFilter(lineupFilter)
                isLineupAttached = false
            }
        }

        btnCoverOverlay.setOnClickListener {
            // deshabilita todos mientras animación
            btnScoreboardOverlay.isEnabled = false
            btnLineupOverlay.isEnabled     = false
            btnCoverOverlay.isEnabled      = false

            // oculta íconos y muestra spinners
            btnScoreboardOverlay.icon = null
            btnLineupOverlay.icon     = null
            btnCoverOverlay.icon      = null
            btnScoreboardOverlay.visibility = View.GONE
            btnLineupOverlay.visibility     = View.GONE
            btnCoverOverlay.visibility      = View.GONE
            spinnerScoreboard.visibility    = View.VISIBLE
            spinnerLineup.visibility        = View.VISIBLE
            spinnerCover.visibility         = View.VISIBLE

            // cambia estado en el ViewModel
            vm.setCoverEnabled(!(vm.coverEnabled.value ?: false))

            // tras 3s, restaurar visibilidad e íconos
            viewLifecycleOwner.lifecycleScope.launch {
                delay(3000)
                spinnerScoreboard.visibility = View.GONE
                spinnerLineup.visibility     = View.GONE
                spinnerCover.visibility      = View.GONE

                btnScoreboardOverlay.icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_scoreboard_overlay
                )
                btnLineupOverlay.icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_lineup_overlay
                )
                btnCoverOverlay.icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_cover_overlay
                )

                btnScoreboardOverlay.visibility = View.VISIBLE
                btnLineupOverlay.visibility     = View.VISIBLE
                btnCoverOverlay.visibility      = View.VISIBLE

                btnScoreboardOverlay.isEnabled = true
                btnLineupOverlay.isEnabled     = true
                btnCoverOverlay.isEnabled      = true
            }
        }

        btnLineupOverlay.setOnClickListener {
            // 1) Deshabilitamos TODOS los botones
            btnScoreboardOverlay.isEnabled = false
            btnLineupOverlay    .isEnabled = false
            btnCoverOverlay     .isEnabled = false

            // 2) Quitamos todos los iconos y ocultamos los botones
            btnScoreboardOverlay.icon = null
            btnLineupOverlay    .icon = null
            btnCoverOverlay     .icon = null

            btnScoreboardOverlay.visibility = View.GONE
            btnLineupOverlay    .visibility = View.GONE
            btnCoverOverlay     .visibility = View.GONE

            // 3) Mostramos TODOS los spinners
            spinnerScoreboard.visibility = View.VISIBLE
            spinnerLineup    .visibility = View.VISIBLE
            spinnerCover     .visibility = View.VISIBLE

            // 4) Cambiamos el estado en el ViewModel
            vm.setLineupEnabled(!(vm.lineupEnabled.value ?: false))

            // 5) Tras el delay, restauramos todo
            viewLifecycleOwner.lifecycleScope.launch {
                delay(3000)

                // Ocultamos spinners
                spinnerScoreboard.visibility = View.GONE
                spinnerLineup    .visibility = View.GONE
                spinnerCover     .visibility = View.GONE

                // Volvemos a poner los iconos
                btnScoreboardOverlay.icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_scoreboard_overlay
                )
                btnLineupOverlay    .icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_lineup_overlay
                )
                btnCoverOverlay     .icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_cover_overlay
                )

                // Restauramos visibilidad según selección actual
                btnScoreboardOverlay.visibility = if (vm.selectedScoreboard.value?.isNotBlank() == true) View.VISIBLE else View.GONE
                btnLineupOverlay    .visibility = if (vm.selectedLineup   .value?.isNotBlank() == true) View.VISIBLE else View.GONE
                btnCoverOverlay     .visibility = if (vm.selectedCover    .value?.isNotBlank() == true) View.VISIBLE else View.GONE

                // Re-habilitamos los botones
                btnScoreboardOverlay.isEnabled = true
                btnLineupOverlay    .isEnabled = true
                btnCoverOverlay     .isEnabled = true
            }
        }

        // 1) Mostrar/ocultar toggle según haya scoreboard configurado
//        vm.selectedScoreboard.observe(viewLifecycleOwner) { name ->
//            val hasOverlay = name.isNotBlank()
//            btnOverlaysToggle.visibility = if (hasOverlay) View.VISIBLE else View.GONE
//
//            if (!hasOverlay) {
//                // Si se quita la configuración, colapsamos el submenú y desactivamos el scoreboard
//                submenuOverlays.visibility = View.GONE
//                vm.setScoreboardEnabled(false)
//            }
//        }
        vm.selectedScoreboard.observe(viewLifecycleOwner) { updateOverlaysToggle() }

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
        btnOverlaysToggle.setOnClickListener {
            if (submenuOverlays.isVisible) {
                submenuOverlays.visibility = View.GONE
            } else {
                // Actualizo visibilidad de cada FrameLayout dentro del submenu
                frameLU.visibility      = if (vm.selectedLineup.value?.isNotBlank() == true) View.VISIBLE else View.GONE
                frameSB.visibility = if (vm.selectedScoreboard.value?.isNotBlank() == true) View.VISIBLE else View.GONE
                frameCoverOverlay.visibility = if (vm.selectedCover.value?.isNotBlank() == true)
                    View.VISIBLE else View.GONE

                // Finalmente abro el submenú sólo si hay al menos uno
                submenuOverlays.visibility = if (frameLU.isVisible || frameSB.isVisible || frameCoverOverlay.isVisible) View.VISIBLE else View.GONE
            }
        }

        // 3) Toggle al click
        btnScoreboardOverlay.setOnClickListener {
            // 1) Deshabilitar todos los botones
            btnScoreboardOverlay.isEnabled = false
            btnLineupOverlay    .isEnabled = false
            btnCoverOverlay     .isEnabled = false

            // 2) Ocultar íconos y mostrar spinners en los tres
            btnScoreboardOverlay.icon = null
            btnLineupOverlay    .icon = null
            btnCoverOverlay     .icon = null

            btnScoreboardOverlay.visibility = View.GONE
            btnLineupOverlay    .visibility = View.GONE
            btnCoverOverlay     .visibility = View.GONE

            spinnerScoreboard.visibility = View.VISIBLE
            spinnerLineup    .visibility = View.VISIBLE
            spinnerCover     .visibility = View.VISIBLE

            // 3) Cambiar estado en el VM
            vm.setScoreboardEnabled(!(vm.scoreboardEnabled.value ?: false))

            // 4) Restaurar tras delay
            viewLifecycleOwner.lifecycleScope.launch {
                delay(3000)

                // Ocultar todos los spinners
                spinnerScoreboard.visibility = View.GONE
                spinnerLineup    .visibility = View.GONE
                spinnerCover     .visibility = View.GONE

                // Restaurar íconos
                btnScoreboardOverlay.icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_scoreboard_overlay
                )
                btnLineupOverlay    .icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_lineup_overlay
                )
                btnCoverOverlay     .icon = ContextCompat.getDrawable(
                    requireContext(), R.drawable.ic_cover_overlay
                )

                // Restaurar visibilidades según selección actual
                btnScoreboardOverlay.visibility = if (vm.selectedScoreboard.value?.isNotBlank() == true) View.VISIBLE else View.GONE
                btnLineupOverlay    .visibility = if (vm.selectedLineup   .value?.isNotBlank() == true) View.VISIBLE else View.GONE
                btnCoverOverlay     .visibility = if (vm.selectedCover    .value?.isNotBlank() == true) View.VISIBLE else View.GONE

                // Re-habilitar todos
                btnScoreboardOverlay.isEnabled = true
                btnLineupOverlay    .isEnabled = true
                btnCoverOverlay     .isEnabled = true
            }
        }

        // 4) Abrir settings
        btnOverlaysMenu.setOnClickListener {
            snapshotBmp = null
            if (vm.scoreboardEnabled.value == true && isScoreboardAttached) {
                genericStream.getGlInterface().removeFilter(scoreboardFilter)
                isScoreboardAttached = false
            }
            // idem con Lineup
            if (vm.lineupEnabled.value == true && isLineupAttached) {
                genericStream.getGlInterface().removeFilter(lineupFilter)
                isLineupAttached = false
            }
            // ¡Y también con Cover!
            if (vm.coverEnabled.value == true && isCoverAttached) {
                genericStream.getGlInterface().removeFilter(coverFilter)
                isCoverAttached = false
            }
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

    private fun attachLineupOverlay() {
        lifecycleScope.launch {
            // 1) Obtenemos el LineupItem
            val item = vm.lineups.value
                ?.firstOrNull { it.name == vm.selectedLineup.value }
                ?: return@launch

            // 2) Sacamos las URLs como non-null (o salimos si faltan)
            val urlPlayers1 = item.build["players1"] ?: return@launch
            val urlPlayers2 = item.build["players2"] ?: return@launch
            val urlTeam1 = item.build["team1"] ?: return@launch
            val urlTeam2 = item.build["team2"] ?: return@launch

            // 3) Descargamos los bitmaps en IO
            val grid1Deferred = async(Dispatchers.IO) { URL(urlPlayers1).downloadBitmap() }
            val grid2Deferred = async(Dispatchers.IO) { URL(urlPlayers2).downloadBitmap() }

            val team1BmpDeferred = async(Dispatchers.IO) { URL(urlTeam1).downloadBitmap() }
            val team2BmpDeferred = async(Dispatchers.IO) { URL(urlTeam2).downloadBitmap() }

            // 4) Cabecera: extraemos equipos seleccionados
            val team1 = vm.teams.value!!.first { it.name == vm.selectedTeam1.value }
            val team2 = vm.teams.value!!.first { it.name == vm.selectedTeam2.value }
            val urlLogo1 = team1.logoUrl ?: return@launch
            val urlLogo2 = team2.logoUrl ?: return@launch
            val logo1Deferred = async(Dispatchers.IO) { URL(urlLogo1).downloadBitmap() }
            val logo2Deferred = async(Dispatchers.IO) { URL(urlLogo2).downloadBitmap() }

            val bmpTeam1 = team1BmpDeferred.await()
            val bmpTeam2 = team2BmpDeferred.await()

            // 5) Esperamos a tener todos los bitmaps
            val grid1  = grid1Deferred.await()
            val grid2  = grid2Deferred.await()
            val logo1  = logo1Deferred.await()
            val logo2  = logo2Deferred.await()

            // 6) Creamos el composite
            val bmp = LineupOverlayGenerator.createCompositeBitmap(
                imgTeam1 = bmpTeam1,
                imgTeam2 = bmpTeam2,
                logo1    = logo1,
                logo2    = logo2,
                teamName1 = team1.name,
                teamName2 = team2.name,
                gridCell1  = grid1,
                gridCell2  = grid2,
                players1   = team1.players,
                players2   = team2.players,
            )
            compositeLineupBmp = bmp

            // 7) Aplicamos filtro
            LineupOverlayGenerator.updateOverlay(
                imgTeam1   = bmpTeam1,
                imgTeam2   = bmpTeam2,
                logo1      = logo1,
                logo2      = logo2,
                teamName1  = team1.name,
                teamName2  = team2.name,
                gridCell1  = grid1,
                gridCell2  = grid2,
                players1   = team1.players,
                players2   = team2.players,
                filter     = lineupFilter
            )
            // En lugar de usar bmpTeam1/bmpTeam2, usa el composite
            val composite = compositeLineupBmp ?: return@launch

            val sw = resources.displayMetrics.widthPixels.toFloat()
            val sh = resources.displayMetrics.heightPixels.toFloat()

            val isLandscape = resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE


            val baseScaleX = composite.width / sw * 100f
            // Escala respecto al tamaño real del composite
            val scaleX = if (isLandscape) {
                baseScaleX - 5f  // menos “recorte” => más grande
            } else {
                baseScaleX - 30f
            }
            val scaleY = if (isLandscape) {
                composite.height / sh * 100f - 2f
            } else {
                composite.height / sh * 100f - 1f
            }

            // Lo centramos horizontal y verticalmente
            val posX = (100f - scaleX) / 2f
            val posY = (100f - scaleY) / 5f
            lineupFilter.setScale(scaleX, scaleY)
            lineupFilter.setPosition(posX, posY)
            genericStream.getGlInterface().addFilter(lineupFilter)
            isLineupAttached = true
        }
    }


    private fun reattachLineupFilter() {
        compositeLineupBmp?.let {
            // 1) Volvemos a componer/hacer update (en UI thread) igual que en attach
            LineupOverlayGenerator.updateOverlay(
                imgTeam1 = it,   // aquí guardamos composite en lugar de zonas
                imgTeam2 = null, // ya no usamos esto; podrías sobrecargar updateOverlay si quieres
                logo1    = null,
                logo2    = null,
                teamName1 = "",
                teamName2 = "",
                gridCell1  = null,
                gridCell2  = null,
                players1   = emptyList(),
                players2   = emptyList(),
                filter   = lineupFilter
            )
            // 2) Solo volvemos a añadir si no estaba
            if (!isLineupAttached) {
                genericStream.getGlInterface().addFilter(lineupFilter)
                isLineupAttached = true
            }
        }
    }

    private fun attachCoverOverlay() {
        lifecycleScope.launch {
            val item = vm.covers.value!!.first { it.name == vm.selectedCover.value }
            val baseBmp = withContext(Dispatchers.IO) { URL(item.build["cover"]!!).downloadBitmap() }
            val team1   = vm.teams.value!!.first { it.name == vm.selectedTeam1.value }
            val team2   = vm.teams.value!!.first { it.name == vm.selectedTeam2.value }
            val logo1   = async(Dispatchers.IO){ URL(team1.logoUrl!!).downloadBitmap() }.await()
            val logo2   = async(Dispatchers.IO){ URL(team2.logoUrl!!).downloadBitmap() }.await()

            // 1) Composición
            val composite = CoverOverlayGenerator.createCompositeBitmap(
                base      = baseBmp,
                logo1     = logo1,
                logo2     = logo2,
                label     = coverLabel,
                teamName1 = team1.name,
                teamName2 = team2.name
            )

            // 2) En UI: setImage, escala y addFilter
            withContext(Dispatchers.Main) {
                coverFilter.setImage(composite)
                applyCoverScaleAndPosition(composite)
                genericStream.getGlInterface().addFilter(coverFilter)
                isCoverAttached = true
            }
        }
    }

    private fun applyCoverScaleAndPosition(bmp: Bitmap) {
        // Copiado de applyScaleAndPosition (Lineup/Scoreboard)
        val metrics    = resources.displayMetrics
        val sw         = metrics.widthPixels.toFloat()
        val sh         = metrics.heightPixels.toFloat()
        val isLandscape = resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

        val baseScaleX = bmp.width / sw * 100f
        val scaleX     = if (isLandscape) baseScaleX + 20f else baseScaleX - 10f
        val scaleY     = if (isLandscape)
            bmp.height / sh * 100f + 20f
        else
            bmp.height / sh * 100f - 1f
        val posX       = (100f - scaleX) / 2f
        val posY       = (100f - scaleY) / 2f

        coverFilter.setScale(scaleX, scaleY)
        coverFilter.setPosition(posX, posY)
    }

    private fun <T> asyncIO(block: suspend ()->T) =
        lifecycleScope.async(Dispatchers.IO) { block() }
    private fun URL.downloadBitmap() =
        openStream().use { BitmapFactory.decodeStream(it) }!!

    private fun updateOverlaysToggle() {
        val hasScore  = vm.selectedScoreboard.value?.isNotBlank() == true
        val hasLineup = vm.selectedLineup.value  ?.isNotBlank() == true
        val hasCover  = vm.selectedCover.value   ?.isNotBlank() == true

        val showToggle = hasScore || hasLineup || hasCover
        btnOverlaysToggle.visibility = if (showToggle) View.VISIBLE else View.GONE

        if (!showToggle) {
            submenuOverlays.visibility = View.GONE
            vm.setScoreboardEnabled(false)
            vm.setLineupEnabled(false)
            vm.setCoverEnabled(false)
        }
    }
}
