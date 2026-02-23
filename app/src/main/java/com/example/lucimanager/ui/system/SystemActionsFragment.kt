package com.example.lucimanager.ui.system

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentSystemActionsBinding
import com.example.lucimanager.repository.NetworkRepository
import com.example.lucimanager.utils.showErrorSnackbar
import com.example.lucimanager.utils.showSuccessSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemActionsFragment : Fragment() {

    private var _binding: FragmentSystemActionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSystemActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rebootButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.system_reboot_confirm_title)
                .setMessage(R.string.system_reboot_confirm_message)
                .setPositiveButton(R.string.system_confirm) { _, _ -> performReboot() }
                .setNegativeButton(R.string.system_cancel, null)
                .show()
        }

        binding.shutdownButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.system_shutdown_confirm_title)
                .setMessage(R.string.system_shutdown_confirm_message)
                .setPositiveButton(R.string.system_confirm) { _, _ -> performShutdown() }
                .setNegativeButton(R.string.system_cancel, null)
                .show()
        }
    }

    private fun performReboot() {
        val repository = NetworkRepository(requireContext())
        CoroutineScope(Dispatchers.Main).launch {
            val result = repository.rebootRouter()
            if (result.isSuccess) {
                showSuccessSnackbar(getString(R.string.system_reboot_success))
            } else {
                showErrorSnackbar(getString(R.string.system_action_error))
            }
        }
    }

    private fun performShutdown() {
        val repository = NetworkRepository(requireContext())
        CoroutineScope(Dispatchers.Main).launch {
            val result = repository.shutdownRouter()
            if (result.isSuccess) {
                showSuccessSnackbar(getString(R.string.system_shutdown_success))
            } else {
                showErrorSnackbar(getString(R.string.system_action_error))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
