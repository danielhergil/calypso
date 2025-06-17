package com.danihg.calypso.camera.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import coil.load
import com.danihg.calypso.R
import com.danihg.calypso.camera.models.CameraViewModel
import com.danihg.calypso.camera.models.OverlaysSettingsViewModel
import com.google.android.material.button.MaterialButton

class OverlaysSettingsFragment : Fragment(R.layout.fragment_overlays_settings) {

    private val cameraViewModel: CameraViewModel by activityViewModels()
    private val vm: OverlaysSettingsViewModel by activityViewModels()
    private val genericStream get() = cameraViewModel.genericStream

    // Teams
    private lateinit var btnClose: MaterialButton
    private lateinit var headerTeams: LinearLayout
    private lateinit var bodyTeams: LinearLayout
    private lateinit var ivTeamsArrow: ImageView
    private lateinit var actTeam1: AutoCompleteTextView
    private lateinit var actTeam2: AutoCompleteTextView
    private lateinit var ivTeam1Logo: ImageView
    private lateinit var ivTeam2Logo: ImageView
    private lateinit var progress1: ProgressBar
    private lateinit var progress2: ProgressBar

    // Scoreboard
    private lateinit var headerScore: LinearLayout
    private lateinit var bodyScore: LinearLayout
    private lateinit var ivScoreArrow: ImageView
    private lateinit var actScore: AutoCompleteTextView
    private lateinit var ivScoreLogo: ImageView
    private lateinit var progressScore: ProgressBar
    private lateinit var cbShowLogos: CheckBox

    // Lineup
    private lateinit var headerLineup: LinearLayout
    private lateinit var bodyLineup:   LinearLayout
    private lateinit var ivLineupArrow: ImageView
    private lateinit var actLineup:       AutoCompleteTextView
    private lateinit var ivLineupSnapshot: ImageView
    private lateinit var progressLineup:    ProgressBar

    private lateinit var btnSave: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ==== Teams init ====
        btnClose           = view.findViewById(R.id.btnCloseOverlaysSettings)
        headerTeams        = view.findViewById(R.id.header_teams)
        bodyTeams          = view.findViewById(R.id.body_teams)
        ivTeamsArrow       = view.findViewById(R.id.iv_teams_arrow)
        actTeam1           = view.findViewById(R.id.act_team1)
        actTeam2           = view.findViewById(R.id.act_team2)
        ivTeam1Logo        = view.findViewById(R.id.iv_team1_logo)
        ivTeam2Logo        = view.findViewById(R.id.iv_team2_logo)
        progress1          = view.findViewById(R.id.progress_team1)
        progress2          = view.findViewById(R.id.progress_team2)
        headerLineup       = view.findViewById(R.id.header_lineup)
        bodyLineup         = view.findViewById(R.id.body_lineup)
        ivLineupArrow      = view.findViewById(R.id.iv_lineup_arrow)
        actLineup          = view.findViewById(R.id.act_lineup)
        ivLineupSnapshot   = view.findViewById(R.id.iv_lineup_snapshot)
        progressLineup     = view.findViewById(R.id.progress_lineup)

        btnClose.setOnClickListener { parentFragmentManager.popBackStack() }
        headerTeams.setOnClickListener {
            val open = bodyTeams.isVisible
            bodyTeams.visibility = if (open) View.GONE else View.VISIBLE
            ivTeamsArrow.rotation = if (open) 0f else 180f
        }

        vm.teams.observe(viewLifecycleOwner) { list ->
            val names = list.map { it.name }
            val adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_list_item_1,
                names)
            actTeam1.setAdapter(adapter)
            actTeam2.setAdapter(adapter)
        }

        vm.selectedTeam1.value?.takeIf(String::isNotBlank)?.let { name ->
            actTeam1.setText(name, false)
            loadLogo(name, ivTeam1Logo, progress1)
        }
        vm.selectedTeam2.value?.takeIf(String::isNotBlank)?.let { name ->
            actTeam2.setText(name, false)
            loadLogo(name, ivTeam2Logo, progress2)
        }

        actTeam1.setOnItemClickListener { _, _, pos, _ ->
            val name = actTeam1.adapter.getItem(pos) as String
            vm.setTeam1(name)
            loadLogo(name, ivTeam1Logo, progress1)
        }
        actTeam1.doOnTextChanged { t, _, _, _ ->
            val name = t?.toString() ?: ""
            vm.setTeam1(name)
            loadLogo(name, ivTeam1Logo, progress1)
        }

        actTeam2.setOnItemClickListener { _, _, pos, _ ->
            val name = actTeam2.adapter.getItem(pos) as String
            vm.setTeam2(name)
            loadLogo(name, ivTeam2Logo, progress2)
        }
        actTeam2.doOnTextChanged { t, _, _, _ ->
            val name = t?.toString() ?: ""
            vm.setTeam2(name)
            loadLogo(name, ivTeam2Logo, progress2)
        }

        // ==== Scoreboard init ====
        headerScore    = view.findViewById(R.id.header_scoreboard)
        bodyScore      = view.findViewById(R.id.body_scoreboard)
        ivScoreArrow   = view.findViewById(R.id.iv_scoreboard_arrow)
        actScore       = view.findViewById(R.id.act_scoreboard)
        ivScoreLogo    = view.findViewById(R.id.iv_scoreboard_logo)
        progressScore  = view.findViewById(R.id.progress_scoreboard)
        cbShowLogos    = view.findViewById(R.id.cb_show_logos)

        headerScore.setOnClickListener {
            val open = bodyScore.visibility == View.VISIBLE
            bodyScore.visibility = if (open) View.GONE else View.VISIBLE
            ivScoreArrow.rotation = if (open) 0f else 180f
        }

        vm.scoreboards.observe(viewLifecycleOwner) { list ->
            val names = list.map { it.name }
            val adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_list_item_1,
                names)
            actScore.setAdapter(adapter)
        }

        vm.selectedScoreboard.value?.takeIf(String::isNotBlank)?.let { name ->
            actScore.setText(name, false)
            loadScoreLogo(name)
        }
        cbShowLogos.isChecked = vm.showLogos.value == true

        actScore.setOnItemClickListener { _, _, pos, _ ->
            val name = actScore.adapter.getItem(pos) as String
            vm.setScoreboard(name)
            loadScoreLogo(name)
        }

        cbShowLogos.setOnCheckedChangeListener { _, checked ->
            vm.setShowLogos(checked)
            vm.selectedScoreboard.value?.let { loadScoreLogo(it) }
        }

        // ==== Save ====
        btnSave = view.findViewById(R.id.btnSaveOverlays)
        btnSave.setOnClickListener {
            genericStream.getGlInterface().clearFilters()
            vm.setScoreboardEnabled(false)
            vm.setScore1(0)
            vm.setScore2(0)
            vm.setShowLogos(cbShowLogos.isChecked)
            vm.setLineupEnabled(false)
            parentFragmentManager.popBackStack()
        }

        headerLineup.setOnClickListener {
            val open = bodyLineup.visibility == View.VISIBLE
            bodyLineup.visibility = if (open) View.GONE else View.VISIBLE
            ivLineupArrow.rotation = if (open) 0f else 180f
        }
        vm.lineups.observe(viewLifecycleOwner) { list ->
            val names  = list.map { it.name }
            val adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_list_item_1,
                names)
            actLineup.setAdapter(adapter)
        }
        vm.selectedLineup.value
            ?.takeIf(String::isNotBlank)
            ?.let { name ->
                actLineup.setText(name, false)
                loadLineupSnapshot(name)
            }
        actLineup.setOnItemClickListener { _, _, pos, _ ->
            val name = actLineup.adapter.getItem(pos) as String
            vm.setLineup(name)
            loadLineupSnapshot(name)
        }
    }

    /** Carga logo de Team */
    private fun loadLogo(
        name: String,
        imageView: ImageView,
        progressBar: ProgressBar
    ) {
        val team = vm.teams.value?.firstOrNull { it.name == name }
        val url  = team?.logoUrl
        if (url.isNullOrBlank()) {
            progressBar.visibility = View.GONE
            imageView.setImageResource(R.drawable.ic_image_placeholder)
            imageView.visibility = View.VISIBLE
            return
        }

        // Esperar a que imageView esté medido
        imageView.post {
            val targetWidth = imageView.width
            val targetHeight = imageView.height

            // Protección extra: evitar crash si el tamaño sigue sin estar disponible
            if (targetWidth <= 0 || targetHeight <= 0) {
                imageView.load(url) {
                    scale(coil.size.Scale.FIT)
                    placeholder(null)
                    error(R.drawable.ic_image_placeholder)
                    listener(
                        onStart = {
                            progressBar.visibility = View.VISIBLE
                            imageView.visibility = View.INVISIBLE
                        },
                        onSuccess = { _, _ ->
                            progressBar.visibility = View.GONE
                            imageView.visibility = View.VISIBLE
                        },
                        onError = { _, _ ->
                            progressBar.visibility = View.GONE
                            imageView.visibility = View.VISIBLE
                        }
                    )
                }
                return@post
            }

            imageView.load(url) {
                size(targetWidth, targetHeight)  // solo si los valores son válidos
                scale(coil.size.Scale.FIT)
                placeholder(null)
                error(R.drawable.ic_image_placeholder)
                listener(
                    onStart = {
                        progressBar.visibility = View.VISIBLE
                        imageView.visibility = View.INVISIBLE
                    },
                    onSuccess = { _, _ ->
                        progressBar.visibility = View.GONE
                        imageView.visibility = View.VISIBLE
                    },
                    onError = { _, _ ->
                        progressBar.visibility = View.GONE
                        imageView.visibility = View.VISIBLE
                    }
                )
            }
        }
    }


    /** Carga logo de Scoreboard (full o no_logo) */
    private fun loadScoreLogo(scoreName: String) {
        val item = vm.scoreboards.value
            ?.firstOrNull { it.name == scoreName }

        // URL según toggle
        val key = if (vm.showLogos.value == true) "full" else "no_logo"
        val url = item?.snapshots?.get(key)

        if (url.isNullOrBlank()) {
            progressScore.visibility = View.GONE
            ivScoreLogo.setImageResource(R.drawable.ic_image_placeholder)
            ivScoreLogo.visibility = View.VISIBLE
        } else {
            ivScoreLogo.visibility = View.INVISIBLE
            progressScore.visibility = View.VISIBLE
            progressScore.bringToFront()
            ivScoreLogo.load(url) {
                placeholder(null)
                error(R.drawable.ic_image_placeholder)
                listener(
                    onStart = {
                        progressScore.visibility = View.VISIBLE
                        ivScoreLogo.visibility = View.INVISIBLE
                    },
                    onSuccess = { _, _ ->
                        progressScore.visibility = View.GONE
                        ivScoreLogo.visibility = View.VISIBLE
                    },
                    onError = { _, _ ->
                        progressScore.visibility = View.GONE
                        ivScoreLogo.visibility = View.VISIBLE
                    }
                )
            }
        }
    }

    private fun loadLineupSnapshot(name: String) {
        val item = vm.lineups.value?.firstOrNull { it.name == name }
        val url  = item?.snapshots?.get("full")

        if (url.isNullOrBlank()) {
            progressLineup.visibility = View.GONE
            ivLineupSnapshot.setImageResource(R.drawable.ic_image_placeholder)
            ivLineupSnapshot.visibility = View.VISIBLE
        } else {
            ivLineupSnapshot.visibility = View.INVISIBLE
            progressLineup.visibility = View.VISIBLE
            ivLineupSnapshot.load(url) {
                placeholder(null); error(R.drawable.ic_image_placeholder)
                listener(
                    onStart = {
                        progressLineup.visibility = View.VISIBLE
                        ivLineupSnapshot.visibility = View.INVISIBLE
                    },
                    onSuccess = { _, _ ->
                        progressLineup.visibility = View.GONE
                        ivLineupSnapshot.visibility = View.VISIBLE
                    },
                    onError = { _, _ ->
                        progressLineup.visibility = View.GONE
                        ivLineupSnapshot.visibility = View.VISIBLE
                    }
                )
            }
        }
    }
}
