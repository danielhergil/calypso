package com.danihg.calypso.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavDeepLinkBuilder
import com.danihg.calypso.MainActivity
import com.danihg.calypso.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        // 1) Configura el dropdown de idiomas
        val autoCompleteLanguage = findViewById<AutoCompleteTextView>(R.id.autoCompleteLanguage)
        val languages = listOf("English")
        val adapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_1,
            languages
        )
        autoCompleteLanguage.setAdapter(adapter)
        autoCompleteLanguage.setText(languages[0], false)

        // 2) Botón Delete Account
        findViewById<MaterialButton>(R.id.btnDeleteAccount)
            .setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.settings_delete_account))
                    .setMessage(getString(R.string.settings_delete_account_confirm_message))
                    .setPositiveButton(getString(R.string.settings_delete_account_btn)) { _, _ ->
                        deleteUserDataAndAccount()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
    }

    private fun deleteUserDataAndAccount() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "No hay usuario logueado", Toast.LENGTH_SHORT).show()
            return
        }

        // 1) Borra el documento en Firestore: users/{uid}
        db.collection("users")
            .document(user.uid)
            .delete()
            .addOnSuccessListener {
                // 2) Una vez borrado en Firestore, intenta borrar la cuenta de Auth
                deleteAuthUser(user)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this,
                    "Error al borrar datos en Firestore: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun deleteAuthUser(user: FirebaseUser) {
        user.delete()
            .addOnSuccessListener {
                Toast.makeText(this,
                    "Cuenta eliminada completamente",
                    Toast.LENGTH_SHORT
                ).show()

                // Fuerza el sign-out
                auth.signOut()
                // Navega al InitialFragment de MainActivity y limpia back-stack
                NavDeepLinkBuilder(this)
                    .setComponentName(MainActivity::class.java)
                    .setGraph(R.navigation.nav_graph)
                    .setDestination(R.id.initialFragment)
                    .createPendingIntent()
                    .send()

                finish()
            }
            .addOnFailureListener { ex ->
                if (ex is FirebaseAuthRecentLoginRequiredException) {
                    // Firebase pide re-autenticación reciente: podemos pedir contraseña de nuevo
                    promptReauthentication(user)
                } else {
                    Toast.makeText(this,
                        "Error al eliminar de Auth: ${ex.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun promptReauthentication(user: FirebaseUser) {
        // Simple dialog para pedir la contraseña y re-autenticar
        val input = AutoCompleteTextView(this).apply {
            hint = "Contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Re-autenticación requerida")
            .setView(input)
            .setPositiveButton("Aceptar") { _, _ ->
                val password = input.text.toString()
                val email = user.email
                if (email.isNullOrBlank() || password.isBlank()) {
                    Toast.makeText(this,
                        "Debe ingresar su contraseña",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        // Tras re-autenticarse, vuelve a intentar el borrado
                        deleteAuthUser(user)
                    }
                    .addOnFailureListener { reAuthEx ->
                        Toast.makeText(this,
                            "Falló re-autenticación: ${reAuthEx.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
