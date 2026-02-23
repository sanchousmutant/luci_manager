package com.example.lucimanager.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentDashboardBinding
import com.example.lucimanager.model.RouterInfo
import com.example.lucimanager.viewmodel.DashboardState
import com.example.lucimanager.viewmodel.DashboardViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadRouterInfo() }
        binding.retryButton.setOnClickListener { viewModel.loadRouterInfo() }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is DashboardState.Loading -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.errorState.visibility = View.GONE
                    binding.cardSystemInfo.visibility = View.GONE
                    binding.cardResources.visibility = View.GONE
                }
                is DashboardState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    binding.cardSystemInfo.visibility = View.VISIBLE
                    binding.cardResources.visibility = View.VISIBLE
                    updateUI(state.routerInfo)
                }
                is DashboardState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.cardSystemInfo.visibility = View.GONE
                    binding.cardResources.visibility = View.GONE
                    binding.errorMessage.text = state.message
                }
            }
        })

        viewModel.loadRouterInfo()
    }

    private fun updateUI(info: RouterInfo) {
        binding.textHostname.text = info.hostname.ifBlank { "-" }
        binding.textModel.text = info.model.ifBlank { "-" }
        binding.textFirmware.text = info.firmwareVersion.ifBlank { "-" }
        binding.textKernel.text = info.kernelVersion.ifBlank { "-" }

        // Uptime
        val days = info.uptime / 86400
        val hours = (info.uptime % 86400) / 3600
        val minutes = (info.uptime % 3600) / 60
        binding.textUptime.text = "${days}${getString(R.string.dashboard_days)} ${hours}${getString(R.string.dashboard_hours)} ${minutes}${getString(R.string.dashboard_minutes)}"

        // CPU Load
        binding.textLoad.text = info.loadAvg.joinToString("  ") { String.format("%.2f", it) }

        // Memory
        binding.memoryProgress.progress = info.memUsagePercent
        val usedMb = info.memUsed / 1024 / 1024
        val freeMb = info.memFree / 1024 / 1024
        val bufferedMb = info.memBuffered / 1024 / 1024
        binding.textMemoryDetail.text = "${getString(R.string.dashboard_memory_used)}: ${usedMb}MB / ${getString(R.string.dashboard_memory_free)}: ${freeMb}MB / ${getString(R.string.dashboard_memory_buffered)}: ${bufferedMb}MB"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
