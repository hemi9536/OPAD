package com.henrasta.opad.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.henrasta.opad.MainActivity
import com.henrasta.opad.databinding.FragmentLoginBinding
import com.henrasta.opad.ui.register.RegisterFragment

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val intent = Intent(activity, MainActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
            val email = binding.username
            val password = binding.password
            val login = binding.login
            val register = binding.register
            val forgotPassword = binding.forgotPasswordBtn

            login.setOnClickListener {
                val emailText = email.text.toString()
                val passwordText = password.text.toString()

                if (validateInput(emailText, passwordText)) {
                    auth.signInWithEmailAndPassword(emailText, passwordText)
                        .addOnCompleteListener(requireActivity()) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    // Navigate to main activity
                                    val intent = Intent(activity, MainActivity::class.java)
                                    startActivity(intent)
                                }
                            } else {
                                Toast.makeText(activity, "Login failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            }

        register.setOnClickListener {
            val fragmentTransaction = activity?.supportFragmentManager?.beginTransaction()
            val f = RegisterFragment()
            fragmentTransaction?.replace(this.id, f, "LoginFragmentTag")
            fragmentTransaction?.addToBackStack(null)
            fragmentTransaction?.commit()
        }

        forgotPassword.setOnClickListener {
            val emailAddress = email.text.toString()
            if (emailAddress.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()) {
                Toast.makeText(activity, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
            } else {
                // Reset pw
                auth.sendPasswordResetEmail(emailAddress)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(activity, "Password reset email sent.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "Error sending password reset email.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(activity, "Please enter an email.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(activity, "Please enter a valid email.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.isEmpty()) {
            Toast.makeText(activity, "Please enter a password.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.length < 6) {
            Toast.makeText(activity, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
