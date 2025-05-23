// src/main/java/com/danihg/calypso/ui/InitialFragment.kt
package com.danihg.calypso.starter.start

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.danihg.calypso.R
import com.danihg.calypso.databinding.FragmentInitialBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Suppress("DEPRECATION")
class InitialFragment : Fragment() {
    private var _binding: FragmentInitialBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var googleClient: GoogleSignInClient
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        if (!cameraGranted || !audioGranted) {
            Toast.makeText(requireContext(), "Camera and audio permissions are required to continue", Toast.LENGTH_LONG).show()
        }
    }

    // replace deprecated startActivityForResult/onActivityResult
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("InitialFragment", "Google sign in failed", e)
                Toast.makeText(requireContext(), "Google sign-in failed.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("InitialFragment", "Google sign in canceled or failed")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Permissions
        val cameraOk = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val audioOk = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!cameraOk || !audioOk) {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }

        // Google Sign-In setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(requireActivity(), gso)

        // Click handlers
        binding.btnSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_initial_to_signUp)
        }
        binding.btnGoogle.setOnClickListener {
            // Ensure account chooser appears every time
            googleClient.signOut().addOnCompleteListener {
                // Launch Google Sign-In intent via ActivityResult API
                googleSignInLauncher.launch(googleClient.signInIntent)
            }
        }
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_initial_to_login)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    findNavController().navigate(R.id.action_initial_to_home)
                } else {
                    Toast.makeText(requireContext(), "Authentication Failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}