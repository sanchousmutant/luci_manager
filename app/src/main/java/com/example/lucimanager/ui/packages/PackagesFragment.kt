package com.example.lucimanager.ui.packages

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentPackagesBinding
import com.example.lucimanager.utils.showErrorSnackbar
import com.example.lucimanager.utils.showSuccessSnackbar
import com.example.lucimanager.viewmodel.PackageActionState
import com.example.lucimanager.viewmodel.PackagesState
import com.example.lucimanager.viewmodel.PackagesViewModel

class PackagesFragment : Fragment() {

    private var _binding: FragmentPackagesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PackagesViewModel by viewModels()
    private lateinit var adapter: PackageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPackagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PackageAdapter(
            onInstallClick = { viewModel.installPackage(it) },
            onRemoveClick = { viewModel.removePackage(it) }
        )
        binding.packagesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.packagesRecycler.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadInstalledPackages() }
        binding.retryButton.setOnClickListener { viewModel.loadInstalledPackages() }
        binding.updateListsButton.setOnClickListener { viewModel.updateLists() }

        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                viewModel.searchPackages(query)
            }
        })

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is PackagesState.Loading -> {
                    binding.progressIndicator.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                }
                is PackagesState.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.GONE
                    if (state.packages.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.packagesRecycler.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.packagesRecycler.visibility = View.VISIBLE
                        adapter.submitList(state.packages)
                    }
                }
                is PackagesState.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    binding.progressIndicator.visibility = View.GONE
                    binding.errorState.visibility = View.VISIBLE
                    binding.packagesRecycler.visibility = View.GONE
                    binding.errorMessage.text = state.message
                }
            }
        })

        viewModel.actionState.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is PackageActionState.Success -> {
                    showSuccessSnackbar(state.message)
                    viewModel.clearActionState()
                }
                is PackageActionState.Error -> {
                    showErrorSnackbar(state.message)
                    viewModel.clearActionState()
                }
                else -> {}
            }
        })

        viewModel.loadInstalledPackages()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
