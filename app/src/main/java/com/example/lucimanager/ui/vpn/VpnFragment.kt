package com.example.lucimanager.ui.vpn

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lucimanager.databinding.FragmentVpnBinding
import com.example.lucimanager.utils.showErrorSnackbar
import com.example.lucimanager.utils.showSuccessSnackbar
import com.example.lucimanager.viewmodel.VpnState
import com.example.lucimanager.viewmodel.VpnToggleState
import com.example.lucimanager.viewmodel.VpnViewModel

class VpnFragment : Fragment() {

    private var _binding: FragmentVpnBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VpnViewModel by viewModels()
    private lateinit var adapter: VpnAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVpnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VpnAdapter { vpn, enable ->
            viewModel.toggleVpn(vpn, enable)
        }
        binding.vpnRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.vpnRecycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadVpnConnections() }
        binding.retryButton.setOnClickListener { viewModel.loadVpnConnections() }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is VpnState.Loading -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                }
                is VpnState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    if (state.connections.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.vpnRecycler.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.vpnRecycler.visibility = View.VISIBLE
                        adapter.submitList(state.connections)
                    }
                }
                is VpnState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.vpnRecycler.visibility = View.GONE
                    binding.errorMessage.text = state.message
                }
            }
        })

        viewModel.toggleState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is VpnToggleState.Idle -> adapter.setLoadingVpn(null)
                is VpnToggleState.Loading -> adapter.setLoadingVpn(state.vpnName)
                is VpnToggleState.Success -> {
                    adapter.setLoadingVpn(null)
                    showSuccessSnackbar(state.vpnName)
                }
                is VpnToggleState.Error -> {
                    adapter.setLoadingVpn(null)
                    showErrorSnackbar(state.message)
                }
            }
        })

        viewModel.loadVpnConnections()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
