package com.danihg.calypso.teams

import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.AllCaps
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.danihg.calypso.R
import com.danihg.calypso.databinding.ActivityAddTeamsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.Serializable

// --- Modelos ---
data class Player(val playerName: String = "", val number: Int = 0)
data class DraftPlayer(val name: String, val number: Int) : Serializable

data class Team(
    val name: String = "",
    val alias: String = "",
    val logo: String = "",
    val createdAt: Timestamp? = null,
    val players: List<Player> = emptyList()
)
data class DraftTeam(
    val name: String,
    val alias: String,
    val logoUri: Uri?,
    val players: List<DraftPlayer>
) : Serializable

class AddTeamsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddTeamsBinding
    private val db        = FirebaseFirestore.getInstance()
    private val storage   = FirebaseStorage.getInstance().reference
    private val userId    by lazy { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    // Reutilizamos variables para edición…
    private var selectedTeamId: String? = null
    private var selectedIvSmall: ImageView? = null
    private var selectedIvLarge: ImageView? = null
    private var selectedEtName: TextInputEditText? = null
    private var selectedProgressBar: ProgressBar? = null
    private var selectedEditButton: MaterialButton? = null
    private var selectedPendingUri: Uri? = null

    // Pick para edición existente
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPendingUri = it
            selectedIvLarge?.load(it) { placeholder(R.drawable.ic_image_placeholder) }
        }
    }

    // Pick para NUEVO equipo
    private lateinit var pickNewLogoLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private var newPendingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTeamsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        // Launcher para nuevo logo
        pickNewLogoLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                newPendingUri = it
                currentNewIv?.load(it) { placeholder(R.drawable.ic_image_placeholder) }
            }
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.fabAddTeam.setOnClickListener { showNewTeamForm() }

        val editingTeamId = savedInstanceState?.getString("editing_team_id")
        val editingTeamData = savedInstanceState?.getSerializable("editing_team_data") as? DraftTeam

//        refreshTeams()
        loadTeams(editingTeamId, editingTeamData)

        savedInstanceState?.getSerializable("draft_team")?.let { data ->
            val draft = data as DraftTeam

            // 🔥 Eliminar cualquier formulario anterior
            (0 until binding.teamsContainer.childCount).mapNotNull { i ->
                binding.teamsContainer.getChildAt(i) as? MaterialCardView
            }.filter { it.tag == "new_team_form" }
                .forEach { binding.teamsContainer.removeView(it) }

            // 🧠 Inflar formulario limpio
            showNewTeamForm(
                draftName = draft.name,
                draftAlias = draft.alias,
                draftLogoUri = draft.logoUri,
                draftPlayers = draft.players
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // 1) Guardar estado del formulario "Nuevo equipo" si está abierto
        val newFormCard = (0 until binding.teamsContainer.childCount)
            .mapNotNull { binding.teamsContainer.getChildAt(it) as? MaterialCardView }
            .firstOrNull { it.tag == "new_team_form" }
        newFormCard?.let { card ->
            val etName      = card.findViewById<TextInputEditText>(R.id.etNewTeamName)
            val etAlias     = card.findViewById<TextInputEditText>(R.id.etNewTeamAlias)
            val playersCont = card.findViewById<LinearLayout>(R.id.newPlayersContainer)

            val draftPlayers = mutableListOf<DraftPlayer>()
            for (i in 0 until playersCont.childCount) {
                val row    = playersCont.getChildAt(i)
                val name   = row.findViewById<TextInputEditText>(R.id.etPlayerName).text.toString()
                val number = row.findViewById<TextInputEditText>(R.id.etPlayerNumber)
                    .text.toString().toIntOrNull() ?: 0
                draftPlayers.add(DraftPlayer(name, number))
            }

            val draftTeam = DraftTeam(
                name     = etName.text.toString(),
                alias    = etAlias.text.toString(),
                logoUri  = newPendingUri,
                players  = draftPlayers
            )
            outState.putSerializable("draft_team", draftTeam)
        }

        // 2) Guardar estado del "team" en edición si hay alguno expandido
        val editingCard = (0 until binding.teamsContainer.childCount)
            .mapNotNull { binding.teamsContainer.getChildAt(it) as? MaterialCardView }
            .firstOrNull {
                it.findViewById<LinearLayout>(R.id.bodyLayout)?.visibility == View.VISIBLE
            }
        editingCard?.let { card ->
            // Recuperamos el teamId que habíamos guardado como tag
            val teamId = card.getTag(R.id.tag_team_id) as? String ?: return@let

            val etName      = card.findViewById<TextInputEditText>(R.id.etTeamName)
            val etAlias     = card.findViewById<TextInputEditText>(R.id.etTeamAlias)
            val playersCont = card.findViewById<LinearLayout>(R.id.playersContainer)

            val draftPlayers = mutableListOf<DraftPlayer>()
            for (i in 0 until playersCont.childCount) {
                val row    = playersCont.getChildAt(i)
                val name   = row.findViewById<TextInputEditText>(R.id.etPlayerName).text.toString()
                val number = row.findViewById<TextInputEditText>(R.id.etPlayerNumber)
                    .text.toString().toIntOrNull() ?: 0
                draftPlayers.add(DraftPlayer(name, number))
            }

            val editingDraft = DraftTeam(
                name     = etName.text.toString(),
                alias    = etAlias.text.toString(),
                logoUri  = selectedPendingUri,
                players  = draftPlayers
            )
            outState.putString("editing_team_id", teamId)
            outState.putSerializable("editing_team_data", editingDraft)
        }
    }


    private fun refreshTeams() {
        binding.teamsContainer.removeAllViews()
        loadTeams()
    }

    private fun loadTeams(
        restoreTeamId: String? = null,
        restoreTeamData: DraftTeam? = null
    ) {
        if (userId.isEmpty()) return
        db.collection("users").document(userId)
            .collection("teams")
            .get()
            .addOnSuccessListener { snaps ->
                snaps.forEach { doc ->
                    createTeamCard(doc.toObject(Team::class.java), doc.id)
                }

                // ✅ Restaurar edición si corresponde
                if (restoreTeamId != null && restoreTeamData != null) {
                    val card = (0 until binding.teamsContainer.childCount)
                        .mapNotNull { binding.teamsContainer.getChildAt(it) as? MaterialCardView }
                        .firstOrNull { it.getTag(R.id.tag_team_id) == restoreTeamId }

                    card?.let {
                        val body = it.findViewById<LinearLayout>(R.id.bodyLayout)
                        val etName = it.findViewById<TextInputEditText>(R.id.etTeamName)
                        val etAlias = it.findViewById<TextInputEditText>(R.id.etTeamAlias)
                        val playersCont = it.findViewById<LinearLayout>(R.id.playersContainer)
                        val ivLarge = it.findViewById<ImageView>(R.id.ivTeamLogoLarge)

                        body.visibility = View.VISIBLE
                        etName.setText(restoreTeamData.name)
                        etAlias.setText(restoreTeamData.alias)
                        playersCont.removeAllViews()
                        restoreTeamData.players.forEach { p ->
                            addPlayerRow(playersCont, p.name, p.number)
                        }
                        restoreTeamData.logoUri?.let { uri ->
                            ivLarge.load(uri) { placeholder(R.drawable.ic_image_placeholder) }
                            selectedPendingUri = uri
                        }
                    }
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Error cargando equipos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createTeamCard(team: Team, teamId: String) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_team_card, binding.teamsContainer, false)
                as MaterialCardView

        card.setTag(R.id.tag_team_id, teamId)

        // Referencias…
        val header           = card.findViewById<LinearLayout>(R.id.headerLayout)
        val ivSmall          = card.findViewById<ImageView>(R.id.ivTeamLogoSmall)
        val tvName           = card.findViewById<TextView>(R.id.tvTeamName)
        val ivArrow          = card.findViewById<ImageView>(R.id.ivArrow)
        val body             = card.findViewById<LinearLayout>(R.id.bodyLayout)
        val etName           = card.findViewById<TextInputEditText>(R.id.etTeamName)
        val etAlias          = card.findViewById<TextInputEditText>(R.id.etTeamAlias)
        val ivLarge          = card.findViewById<ImageView>(R.id.ivTeamLogoLarge)
        val progressLogo     = card.findViewById<ProgressBar>(R.id.progressLogo)
        val btnEditLogo      = card.findViewById<MaterialButton>(R.id.btnEditLogo)
        val playersContainer = card.findViewById<LinearLayout>(R.id.playersContainer)
        val btnAddPlayer     = card.findViewById<Button>(R.id.btnAddPlayer)
        val btnSave          = card.findViewById<Button>(R.id.btnSaveTeam)
        val btnDelete        = card.findViewById<Button>(R.id.btnDeleteTeam)

        // Poblar datos…
        ivSmall.load(team.logo){ placeholder(R.drawable.ic_image_placeholder) }
        tvName.text = team.name
        etName.setText(team.name)
        etAlias.setText(team.alias)
        ivLarge.load(team.logo){ placeholder(R.drawable.ic_image_placeholder) }
        team.players.forEach { addPlayerRow(playersContainer, it.playerName, it.number) }

        // Expand/collapse
        header.setOnClickListener {
            val show = body.visibility == View.GONE
            body.visibility = if (show) View.VISIBLE else View.GONE
            ivArrow.rotation = if (show) 180f else 0f
        }

        // Preparar edición de logo
        btnEditLogo.setOnClickListener {
            selectedTeamId      = teamId
            selectedIvSmall     = ivSmall
            selectedIvLarge     = ivLarge
            selectedEtName      = etName
            selectedProgressBar = progressLogo
            selectedEditButton  = btnEditLogo
            selectedPendingUri  = null
            pickImageLauncher.launch("image/*")
        }

        // Añadir jugador
        btnAddPlayer.setOnClickListener {
            addPlayerRow(playersContainer, "", 0)
        }

        // Guardar cambios
        btnSave.setOnClickListener {
            val nameTxt = etName.text.toString().trim()
            val aliasTxt= etAlias.text.toString().trim()
            val updatedPlayers = mutableListOf<Player>().apply {
                for (i in 0 until playersContainer.childCount) {
                    val row = playersContainer.getChildAt(i)
                    val pName = row.findViewById<TextInputEditText>(R.id.etPlayerName)
                        .text.toString().trim()
                    val pNum  = row.findViewById<TextInputEditText>(R.id.etPlayerNumber)
                        .text.toString().toIntOrNull() ?: 0
                    if (pName.isNotEmpty()) add(Player(pName, pNum))
                }
            }

            // Función que actualiza Firestore
            fun updateFirestore(logoUrl: String?) {
                val data = mutableMapOf<String, Any>(
                    "name"    to nameTxt,
                    "alias"   to aliasTxt,
                    "players" to updatedPlayers
                )
                logoUrl?.let { data["logo"] = it }

                db.collection("users").document(userId)
                    .collection("teams").document(teamId)
                    .update(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Equipo guardado", Toast.LENGTH_SHORT).show()
                        refreshTeams()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
                        // Revertir UI
                        selectedProgressBar?.visibility = View.GONE
                        selectedIvLarge?.visibility      = View.VISIBLE
                        selectedEditButton?.visibility   = View.VISIBLE
                    }
            }

            // Si hay logo pendiente, súbelo primero
            selectedPendingUri?.let { uri ->
                selectedIvLarge?.visibility      = View.GONE
                selectedProgressBar?.visibility  = View.VISIBLE
                selectedEditButton?.visibility   = View.GONE

                val ref = storage.child("$userId/$nameTxt.jpeg")
                ref.putFile(uri)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) task.exception?.let { throw it }
                        ref.downloadUrl
                    }
                    .addOnSuccessListener { dlUrl ->
                        updateFirestore(dlUrl.toString())
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al subir logo", Toast.LENGTH_SHORT).show()
                        selectedProgressBar?.visibility = View.GONE
                        selectedIvLarge?.visibility      = View.VISIBLE
                        selectedEditButton?.visibility   = View.VISIBLE
                    }
                return@setOnClickListener
            }

            // Si no, solo Firestore
            updateFirestore(null)
        }

        // Eliminar
        btnDelete.setOnClickListener {
            db.collection("users").document(userId)
                .collection("teams").document(teamId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Equipo eliminado", Toast.LENGTH_SHORT).show()
                    refreshTeams()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show()
                }
        }

        binding.teamsContainer.addView(card)
    }

    // Variables temporales para el formulario nuevo
    private var currentNewIv: ImageView? = null
    private var currentNewProgress: ProgressBar? = null
    private var currentNewNameEt: TextInputEditText? = null

    private fun showNewTeamForm(
        draftName: String = "",
        draftAlias: String = "",
        draftLogoUri: Uri? = null,
        draftPlayers: List<DraftPlayer>? = null
    ) {
        // Inflar form
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_new_team_form, binding.teamsContainer, false)
                as MaterialCardView

        card.tag = "new_team_form"

        val etName = card.findViewById<TextInputEditText>(R.id.etNewTeamName)
        val etAlias = card.findViewById<TextInputEditText>(R.id.etNewTeamAlias)
        val ivLogo = card.findViewById<ImageView>(R.id.ivNewLogoLarge)
        val playersCont = card.findViewById<LinearLayout>(R.id.newPlayersContainer)

        // Setear valores
        etName.setText(draftName)
        etAlias.setText(draftAlias)
        if (draftLogoUri != null) {
            newPendingUri = draftLogoUri
            ivLogo.load(draftLogoUri) { placeholder(R.drawable.ic_image_placeholder) }
        }
        draftPlayers?.forEach {
            addPlayerRow(playersCont, it.name, it.number)
        }

        // Refs
        val pBarLogo     = card.findViewById<ProgressBar>(R.id.progressNewLogo)
        val btnPickLogo  = card.findViewById<MaterialButton>(R.id.btnPickNewLogo)
        val btnAddPl     = card.findViewById<Button>(R.id.btnAddNewPlayer)
        val btnSaveNew   = card.findViewById<Button>(R.id.btnSaveNewTeam)

        // Filtros de input
        etName.filters  = arrayOf(InputFilter.LengthFilter(15))
        etAlias.filters = arrayOf(InputFilter.LengthFilter(3), AllCaps())

        // Guardar refs globales para launcher
        currentNewIv     = ivLogo
        currentNewProgress = pBarLogo
        currentNewNameEt = etName

        // Pick logo
        btnPickLogo.setOnClickListener {
            newPendingUri = null
            pickNewLogoLauncher.launch("image/*")
        }

        // Añadir jugador
        btnAddPl.setOnClickListener {
            addPlayerRow(playersCont, "", 0)
        }


        // Guardar nuevo equipo
        btnSaveNew.setOnClickListener {
            val nameTxt  = etName.text.toString().trim()
            val aliasTxt = etAlias.text.toString().trim()
            if (nameTxt.isEmpty() || aliasTxt.isEmpty()) {
                Toast.makeText(this, "Nombre y alias son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newPlayers = mutableListOf<Player>().apply {
                for (i in 0 until playersCont.childCount) {
                    val row = playersCont.getChildAt(i)
                    val pName = row.findViewById<TextInputEditText>(R.id.etPlayerName)
                        .text.toString().trim()
                    val pNum  = row.findViewById<TextInputEditText>(R.id.etPlayerNumber)
                        .text.toString().toIntOrNull() ?: 0
                    if (pName.isNotEmpty()) add(Player(pName, pNum))
                }
            }

            // Crear doc nuevo
            val newRef = db.collection("users").document(userId)
                .collection("teams").document()
            val docId = newRef.id

            // Función para setear Firestore
            fun writeNew(logoUrl: String?) {
                val now = Timestamp.now()
                val data = mutableMapOf<String, Any>(
                    "name"      to nameTxt,
                    "alias"     to aliasTxt,
                    "createdAt" to now,
                    "players"   to newPlayers
                )
                logoUrl?.let { data["logo"] = it }
                newRef.set(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Equipo añadido", Toast.LENGTH_SHORT).show()
                        refreshTeams()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al crear equipo", Toast.LENGTH_SHORT).show()
                    }
            }

            // Si hay logo pendiente: subir primero
            newPendingUri?.let { uri ->
                ivLogo.visibility    = View.GONE
                pBarLogo.visibility  = View.VISIBLE
                btnPickLogo.visibility = View.GONE

                val ref = storage.child("$userId/$nameTxt.jpeg")
                ref.putFile(uri)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) t.exception?.let { throw it }
                        ref.downloadUrl
                    }
                    .addOnSuccessListener { dlUrl ->
                        writeNew(dlUrl.toString())
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al subir logo", Toast.LENGTH_SHORT).show()
                    }
                return@setOnClickListener
            }

            // Si no: solo Firestore
            writeNew(null)
        }

        // Insertar form arriba de todo
        binding.teamsContainer.addView(card, 0)
    }

    // Reutiliza para ambos casos (editar y nuevo)
    private fun addPlayerRow(container: LinearLayout, name: String, number: Int) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_player_row, container, false)
        val etName = row.findViewById<TextInputEditText>(R.id.etPlayerName)
        val etNumber = row.findViewById<TextInputEditText>(R.id.etPlayerNumber)
        etName.setText(name)
        etNumber.setText(number.toString())
        row.findViewById<ImageButton>(R.id.btnRemovePlayer).setOnClickListener {
            container.removeView(row)
        }
        container.addView(row)
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
