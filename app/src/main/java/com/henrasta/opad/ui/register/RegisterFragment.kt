package com.henrasta.opad.ui.register

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.henrasta.opad.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val email = binding.username
        val password = binding.password
        val back = binding.backButton
        val register = binding.register
        val confirmPassword = binding.confirmPassword

        val fragmentManager = activity?.supportFragmentManager

        register.setOnClickListener {
            if (validateInput(email.text.toString(), password.text.toString(), confirmPassword.text.toString())) {
                auth.createUserWithEmailAndPassword(email.text.toString(), password.text.toString())
                    .addOnCompleteListener(requireActivity()) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(activity, "Registration successful", Toast.LENGTH_LONG).show()
                            fragmentManager?.popBackStack()
                            fragmentManager?.beginTransaction()?.commit()
                        } else {
                            when (task.exception) {
                                is FirebaseAuthUserCollisionException -> {
                                    Toast.makeText(activity, "This email is already in use.", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    Toast.makeText(activity, "Register failed.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            }
        }

        back.setOnClickListener {
            fragmentManager?.popBackStack()
            fragmentManager?.beginTransaction()?.commit()
        }
    }

    private fun validateInput(email: String, password: String, confirmPassword: String): Boolean {
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
        if (password != confirmPassword) {
            Toast.makeText(activity, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
