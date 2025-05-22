package com.danihg.calypso.ui.start

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.danihg.calypso.R
import com.danihg.calypso.databinding.FragmentSignUpBinding
import com.google.firebase.auth.FirebaseAuth

class SignupFragment : Fragment() {
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    private var passwordVisible = false
    private var confirmVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Toggle password visibility
        binding.btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            val (inputType, icon) = if (passwordVisible) {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD to R.drawable.ic_visibility_off
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD to R.drawable.ic_visibility
            }
            binding.etPassword.inputType = inputType
            binding.btnTogglePassword.setImageResource(icon)
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
        }

        // Toggle confirm visibility
        binding.btnToggleConfirm.setOnClickListener {
            confirmVisible = !confirmVisible
            val (inputType, icon) = if (confirmVisible) {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD to R.drawable.ic_visibility_off
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD to R.drawable.ic_visibility
            }
            binding.etConfirm.inputType = inputType
            binding.btnToggleConfirm.setImageResource(icon)
            binding.etConfirm.setSelection(binding.etConfirm.text?.length ?: 0)
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString()
            val confirm = binding.etConfirm.text.toString()
            var valid = true

            // Email format
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Invalid email"
                valid = false
            }

            // Password length
            if (pass.length < 6) {
                binding.etPassword.error = "Min 6 chars"
                valid = false
            }

            // Match confirm
            if (confirm != pass) {
                binding.etConfirm.error = "Doesn't match"
                valid = false
            }

            if (valid) {
                showLoading(true)
                // Check if user already exists
                auth.fetchSignInMethodsForEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val methods = task.result?.signInMethods
                            if (!methods.isNullOrEmpty()) {
                                // User exists
                                binding.etEmail.error = "Email already in use"
                                Toast.makeText(requireContext(), "Email already registered", Toast.LENGTH_SHORT).show()
                                showLoading(false)
                            } else {
                                // Register new user
                                auth.createUserWithEmailAndPassword(email, pass)
                                    .addOnCompleteListener { createTask ->
                                        showLoading(false)
                                        if (createTask.isSuccessful) {
                                            Toast.makeText(requireContext(), "Registration successful", Toast.LENGTH_SHORT).show()
                                            findNavController().navigate(R.id.action_signUp_to_initial)
                                        } else {
                                            Toast.makeText(requireContext(), createTask.exception?.localizedMessage ?: "Registration failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        } else {
                            showLoading(false)
                            Toast.makeText(requireContext(), "Error checking email existence", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(requireContext(), "Please fix errors", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.blurOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
