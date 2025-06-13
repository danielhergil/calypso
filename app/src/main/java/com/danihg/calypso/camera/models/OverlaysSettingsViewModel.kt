// OverlaysSettingsViewModel.kt
package com.danihg.calypso.camera.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// 1) Modelo de datos para Team
data class Team(
    val id: String              = "",
    val alias: String           = "",
    val createdAt: Timestamp?   = null,
    val logoUrl: String?        = null,
    val name: String            = "",
    val players: List<Player>   = emptyList()
)

data class Player(
    val name: String = "",
    val number: Int  = 0,
    val goals: Int   = 0
)

data class ScoreboardItem(
    val id: String = "",
    val name: String = "",
    val snapshots: Map<String,String> = emptyMap()
)

class OverlaysSettingsViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _teams = MutableLiveData<List<Team>>(emptyList())
    val teams: LiveData<List<Team>> = _teams

    private val _scoreboards = MutableLiveData<List<ScoreboardItem>>(emptyList())
    val scoreboards: LiveData<List<ScoreboardItem>> = _scoreboards

    companion object {
        private const val TAG        = "OverlaysVM"
        private const val KEY_TEAM1  = "key_team1"
        private const val KEY_TEAM2  = "key_team2"
        private const val KEY_SCOREBOARD_NAME = "key_scoreboard_name"
        private const val KEY_SHOW_LOGOS      = "key_show_logos"
        private const val KEY_SCOREBOARD_ENABLED = "key_scoreboard_enabled"
        private const val KEY_SCORE1 = "key_score1"
        private const val KEY_SCORE2 = "key_score2"
    }

    val selectedTeam1 = savedStateHandle.getLiveData(KEY_TEAM1, "")
    val selectedTeam2 = savedStateHandle.getLiveData(KEY_TEAM2, "")
    val selectedScoreboard: MutableLiveData<String> =
        savedStateHandle.getLiveData(KEY_SCOREBOARD_NAME, "")
    val showLogos: MutableLiveData<Boolean> =
        savedStateHandle.getLiveData(KEY_SHOW_LOGOS, true)
    val scoreboardEnabled = savedStateHandle
        .getLiveData(KEY_SCOREBOARD_ENABLED, false)
    val score1 = savedStateHandle.getLiveData(KEY_SCORE1, 0)
    val score2 = savedStateHandle.getLiveData(KEY_SCORE2, 0)

    init {
        viewModelScope.launch {
            fetchUserTeams()
            fetchScoreboardItems()
        }
    }

    private suspend fun fetchScoreboardItems() {
        try {
            // Leemos los docs de /scoreboard
            val snap = db.collection("scoreboard")
                .get()
                .await()

            val list = snap.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                // snapshots se almacena en un Map<String,String>
                val raw = doc.get("snapshots") as? Map<*, *>
                val snaps = raw
                    ?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String
                        val url = v as? String
                        if (key != null && url != null) key to url else null
                    }
                    ?.toMap()
                    ?: emptyMap()

                ScoreboardItem(
                    id        = doc.id,
                    name      = name,
                    snapshots = snaps
                )
            }

            _scoreboards.postValue(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scoreboard items", e)
            _scoreboards.postValue(emptyList())
        }
    }

    fun setScoreboard(name: String) {
        savedStateHandle[KEY_SCOREBOARD_NAME] = name
    }
    fun setShowLogos(show: Boolean) {
        savedStateHandle[KEY_SHOW_LOGOS] = show
    }

    /**
     * Consulta /users/{uid}/teams en Firestore, mapea a Team, y publica en _teams.
     */
    private suspend fun fetchUserTeams() {
        val user = auth.currentUser
        Log.d(TAG, "Current user: $user (uid=${user?.uid})")
        if (user == null) {
            Log.w(TAG, "Usuario no autenticado → no carga equipos")
            return
        }
        try {
            val snapshot = db
                .collection("users")
                .document(user.uid)
                .collection("teams")
                .get()
                .await()

            Log.d(TAG, "Firestore snapshot.documents.size = ${snapshot.documents.size}")

            val list = snapshot.documents.mapNotNull { doc ->
                val alias     = doc.getString("alias") ?: return@mapNotNull null
                val createdAt = doc.getTimestamp("createdAt")
                val logoUrl   = doc.getString("logo")
                val name      = doc.getString("name") ?: ""

                // casteo seguro de players
                val rawPlayers = doc.get("players") as? List<*>
                val players = rawPlayers
                    ?.mapNotNull { item ->
                        (item as? Map<*, *>)?.let { m ->
                            // extraemos de forma segura cada campo
                            val pname  = m["name"]   as? String ?: ""
                            val pnum   = (m["number"] as? Number)?.toInt() ?: 0
                            val pgoals = (m["goals"]  as? Number)?.toInt() ?: 0
                            Player(pname, pnum, pgoals)
                        }
                    }
                    ?: emptyList()

                Team(
                    id        = doc.id,
                    alias     = alias,
                    createdAt = createdAt,
                    logoUrl   = logoUrl,
                    name      = name,
                    players   = players
                )
            }

            Log.d(TAG, "Parsed ${list.size} teams: ${list.map { it.alias }}")
            _teams.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching teams", e)
            _teams.value = emptyList()
        }
    }

    // Métodos para actualizar la selección y que quede guardada
    fun setTeam1(alias: String) {
        savedStateHandle[KEY_TEAM1] = alias
    }
    fun setTeam2(alias: String) {
        savedStateHandle[KEY_TEAM2] = alias
    }
    fun setScoreboardEnabled(enabled: Boolean) {
        savedStateHandle[KEY_SCOREBOARD_ENABLED] = enabled
    }
    fun setScore1(value: Int) {
        savedStateHandle[KEY_SCORE1] = value
    }
    fun setScore2(value: Int) {
        savedStateHandle[KEY_SCORE2] = value
    }
}
