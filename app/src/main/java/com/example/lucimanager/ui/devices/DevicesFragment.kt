package com.example.lucimanager.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lucimanager.databinding.FragmentDevicesBinding
import com.example.lucimanager.viewmodel.DevicesState
import com.example.lucimanager.viewmodel.DevicesViewModel

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DevicesViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeviceAdapter()
        binding.devicesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.devicesRecycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadDevices() }
        binding.retryButton.setOnClickListener { viewModel.loadDevices() }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is DevicesState.Loading -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                }
                is DevicesState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    if (state.devices.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.devicesRecycler.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.devicesRecycler.visibility = View.VISIBLE
                        adapter.submitList(state.devices)
                    }
                }
                is DevicesState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.devicesRecycler.visibility = View.GONE
                    binding.errorMessage.text = state.message
                }
            }
        })

        viewModel.loadDevices()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
