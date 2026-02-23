package com.example.lucimanager.ui.interfaces

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentInterfacesBinding
import com.example.lucimanager.utils.showErrorSnackbar
import com.example.lucimanager.utils.showSuccessSnackbar
import com.example.lucimanager.viewmodel.InterfaceViewModel
import com.example.lucimanager.viewmodel.InterfaceState
import com.example.lucimanager.viewmodel.ToggleState

class InterfacesFragment : Fragment() {

    private var _binding: FragmentInterfacesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NetworkInterfaceAdapter
    private val viewModel: InterfaceViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterfacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        viewModel.loadInterfaces()
    }

    private fun setupRecyclerView() {
        adapter = NetworkInterfaceAdapter { interfaceName, enable ->
            viewModel.toggleInterface(interfaceName, enable)
        }

        binding.interfacesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InterfacesFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadInterfaces()
        }
    }

    private fun observeViewModel() {
        viewModel.interfaceState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is InterfaceState.Loading -> {
                    showLoading(true)
                }
                is InterfaceState.Success -> {
                    showLoading(false)
                    adapter.submitList(state.interfaces)
                    if (state.interfaces.isEmpty()) {
                        showEmptyState(true)
                    } else {
                        showEmptyState(false)
                    }
                    showErrorState(false)
                }
                is InterfaceState.Error -> {
                    showLoading(false)
                    showErrorState(true, state.message)
                    showEmptyState(false)
                }
            }
        })

        viewModel.toggleState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is ToggleState.Idle -> {
                    adapter.setLoadingInterface(null)
                }
                is ToggleState.Loading -> {
                    adapter.setLoadingInterface(state.interfaceName)
                }
                is ToggleState.Success -> {
                    adapter.setLoadingInterface(null)
                    showSuccessSnackbar(getString(
                        if (state.enabled) R.string.success_interface_enabled
                        else R.string.success_interface_disabled
                    ))
                }
                is ToggleState.Error -> {
                    adapter.setLoadingInterface(null)
                    showErrorSnackbar(state.message)
                }
            }
        })
    }

    private fun showLoading(show: Boolean) {
        binding.swipeRefresh.isRefreshing = show
        binding.progressIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        binding.emptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.interfacesRecycler.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showErrorState(show: Boolean, message: String = "") {
        binding.errorState.visibility = if (show) View.VISIBLE else View.GONE
        binding.interfacesRecycler.visibility = if (show) View.GONE else View.VISIBLE
        if (show && message.isNotEmpty()) {
            binding.errorMessage.text = message
        }
        binding.retryButton.setOnClickListener { viewModel.loadInterfaces() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
