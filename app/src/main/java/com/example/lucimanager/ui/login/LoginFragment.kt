package com.example.lucimanager.ui.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentLoginBinding
import com.example.lucimanager.utils.hideKeyboard
import com.example.lucimanager.utils.showErrorSnackbar
import com.example.lucimanager.utils.showSuccessSnackbar
import com.example.lucimanager.viewmodel.LoginState
import com.example.lucimanager.viewmodel.LoginViewModel

class LoginFragment : Fragment() {
    
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: LoginViewModel by viewModels()
    
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
        
        setupInputListeners()
        setupLoginButton()
        observeViewModel()
    }
    
    private fun setupInputListeners() {
        // Clear error when user starts typing
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.clearError()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        
        binding.ipAddressEditText.addTextChangedListener(textWatcher)
        binding.usernameEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.addTextChangedListener(textWatcher)
    }
    
    private fun setupLoginButton() {
        binding.loginButton.setOnClickListener {
            val ipAddress = binding.ipAddressEditText.text.toString().trim()
            val username = binding.usernameEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            
            // Hide keyboard
            binding.root.hideKeyboard()
            
            // Attempt login
            viewModel.login(ipAddress, username, password)
        }
    }
    
    private fun observeViewModel() {
        // Observe login state
        viewModel.loginState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is LoginState.Idle -> {
                    // Do nothing
                }
                is LoginState.Loading -> {
                    showLoading(true)
                }
                is LoginState.Success -> {
                    showLoading(false)
                    showSuccessSnackbar("Login successful!")
                    
                    // Navigate to interfaces screen
                    findNavController().navigate(R.id.action_login_to_interfaces)
                }
                is LoginState.Error -> {
                    showLoading(false)
                    showErrorSnackbar(state.message)
                }
            }
        })
        
        // Observe saved credentials
        viewModel.savedCredentials.observe(viewLifecycleOwner, Observer { credentials ->
            credentials?.let {
                binding.ipAddressEditText.setText(it.ipAddress)
                binding.usernameEditText.setText(it.username)
                binding.passwordEditText.setText(it.password)
            }
        })
    }
    
    private fun showLoading(show: Boolean) {
        binding.loginButton.isEnabled = !show
        binding.loginProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.loginButton.text = if (show) "" else getString(R.string.button_connect)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}