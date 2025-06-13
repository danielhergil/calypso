package com.danihg.calypso.starter.start

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.danihg.calypso.CalypsoApp
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

        val fgsMicGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms[Manifest.permission.FOREGROUND_SERVICE_MICROPHONE] == true
        } else {
            true
        }

        val fgsCameraGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            perms[Manifest.permission.FOREGROUND_SERVICE_CAMERA] == true
        } else {
            true
        }

        if (cameraGranted && audioGranted && fgsMicGranted && fgsCameraGranted) {
            // Aquí ya tenemos todos los permisos → inicializamos el audio
            (requireActivity().application as CalypsoApp).initializeAudioIfNeeded()
        } else {
//            Toast.makeText(
//                requireContext(),
//                "Camera, audio y permisos de servicio en primer plano son necesarios para continuar",
//                Toast.LENGTH_LONG
//            ).show()
        }
    }

    // Reemplaza startActivityForResult/onActivityResult
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val task: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), "Google sign-in failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // 1) Si ya tenemos todos los permisos, los usamos inmediatamente:
        if (hasAllRequiredPermissions()) {
            (requireActivity().application as CalypsoApp).initializeAudioIfNeeded()
        } else {
            // 2) Si no, pedimos los que falten:
            requestMissingPermissions()
        }

        // Google Sign-In setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.btnSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_initial_to_signUp)
        }
        binding.btnGoogle.setOnClickListener {
            googleClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleClient.signInIntent)
            }
        }
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_initial_to_login)
        }
    }

    private fun hasAllRequiredPermissions(): Boolean {
        val ctx = requireContext()
        val cameraGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val fgsMicGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val fgsCameraGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.FOREGROUND_SERVICE_CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return cameraGranted && audioGranted && fgsMicGranted && fgsCameraGranted
    }

    private fun requestMissingPermissions() {
        val permsToRequest = mutableListOf<String>()
        val ctx = requireContext()

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.FOREGROUND_SERVICE_CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
            }
        }

        if (permsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permsToRequest.toTypedArray())
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
