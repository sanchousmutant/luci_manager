package com.example.lucimanager.ui.wifi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentWifiBinding
import com.example.lucimanager.model.WifiNetwork
import com.example.lucimanager.utils.showErrorSnackbar
import com.example.lucimanager.utils.showSuccessSnackbar
import com.example.lucimanager.viewmodel.WifiEditState
import com.example.lucimanager.viewmodel.WifiState
import com.example.lucimanager.viewmodel.WifiViewModel

class WifiFragment : Fragment() {

    private var _binding: FragmentWifiBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WifiViewModel by viewModels()
    private lateinit var adapter: WifiAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WifiAdapter { network -> showEditDialog(network) }
        binding.wifiRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.wifiRecycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadWifiNetworks() }
        binding.retryButton.setOnClickListener { viewModel.loadWifiNetworks() }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is WifiState.Loading -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                }
                is WifiState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    if (state.networks.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.wifiRecycler.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.wifiRecycler.visibility = View.VISIBLE
                        adapter.submitList(state.networks)
                    }
                }
                is WifiState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.wifiRecycler.visibility = View.GONE
                    binding.errorMessage.text = state.message
                }
            }
        })

        viewModel.editState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is WifiEditState.Success -> {
                    showSuccessSnackbar(getString(R.string.wifi_apply_success))
                    viewModel.clearEditState()
                }
                is WifiEditState.Error -> {
                    showErrorSnackbar(state.message)
                    viewModel.clearEditState()
                }
                else -> {}
            }
        })

        viewModel.loadWifiNetworks()
    }

    private fun showEditDialog(network: WifiNetwork) {
        val dialog = WifiEditDialogFragment.newInstance(network)
        dialog.onSave = { updatedNetwork ->
            viewModel.updateWifiNetwork(updatedNetwork)
        }
        dialog.show(childFragmentManager, "wifi_edit")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
