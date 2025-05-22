package com.danihg.calypso.ui.start

import android.content.Context
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
import com.danihg.calypso.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    private var passwordVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentLoginBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Load saved creds
        val prefs = requireContext().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("email", "") ?: ""
        val savedPass = prefs.getString("password", "") ?: ""
        val remember = prefs.getBoolean("remember", false)
        binding.etEmail.setText(savedEmail)
        binding.etPassword.setText(savedPass)
        binding.cbRemember.isChecked = remember

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

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString()
            var valid = true

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Invalid email"
                valid = false
            }
            if (pass.isEmpty()) {
                binding.etPassword.error = "Enter password"
                valid = false
            }

            if (valid) {
                showLoading(true)
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener { task ->
                        showLoading(false)
                        if (task.isSuccessful) {
                            // Save creds if checked
                            if (binding.cbRemember.isChecked) {
                                prefs.edit().apply {
                                    putString("email", email)
                                    putString("password", pass)
                                    putBoolean("remember", true)
                                    apply()
                                }
                            } else {
                                prefs.edit().clear().apply()
                            }
                            Toast.makeText(requireContext(), "Login successful", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_login_to_home)
                        } else {
                            Toast.makeText(requireContext(), task.exception?.localizedMessage ?: "Login failed", Toast.LENGTH_SHORT).show()
                        }
                    }
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