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

// --- Modelos ---
data class Player(val playerName: String = "", val number: Int = 0)
data class Team(
    val name: String = "",
    val alias: String = "",
    val logo: String = "",
    val createdAt: Timestamp? = null,
    val players: List<Player> = emptyList()
)

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
        refreshTeams()
    }

    private fun refreshTeams() {
        binding.teamsContainer.removeAllViews()
        loadTeams()
    }

    private fun loadTeams() {
        if (userId.isEmpty()) return
        db.collection("users").document(userId)
            .collection("teams")
            .get()
            .addOnSuccessListener { snaps ->
                snaps.forEach { doc ->
                    createTeamCard(doc.toObject(Team::class.java), doc.id)
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Error cargando equipos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createTeamCard(team: Team, teamId: String) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_team_card, binding.teamsContainer, false)
                as MaterialCardView

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

    private fun showNewTeamForm() {
        // Inflar form
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_new_team_form, binding.teamsContainer, false)
                as MaterialCardView

        // Refs
        val etName       = card.findViewById<TextInputEditText>(R.id.etNewTeamName)
        val etAlias      = card.findViewById<TextInputEditText>(R.id.etNewTeamAlias)
        val ivLogo       = card.findViewById<ImageView>(R.id.ivNewLogoLarge)
        val pBarLogo     = card.findViewById<ProgressBar>(R.id.progressNewLogo)
        val btnPickLogo  = card.findViewById<MaterialButton>(R.id.btnPickNewLogo)
        val playersCont  = card.findViewById<LinearLayout>(R.id.newPlayersContainer)
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
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_player_row, container, false)
        val etName   = row.findViewById<TextInputEditText>(R.id.etPlayerName)
        val etNumber = row.findViewById<TextInputEditText>(R.id.etPlayerNumber)
        val btnRm    = row.findViewById<ImageButton>(R.id.btnRemovePlayer)

        etName.setText(name)
        etNumber.setText(number.toString())
        btnRm.setOnClickListener { container.removeView(row) }
        container.addView(row)
    }
}
