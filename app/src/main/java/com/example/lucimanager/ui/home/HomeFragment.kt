package com.example.lucimanager.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentHomeBinding
import com.example.lucimanager.network.LuciApiClient
import com.example.lucimanager.repository.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var toggle: ActionBarDrawerToggle
    private var childNavController: NavController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navHostFragment = childFragmentManager
            .findFragmentById(R.id.home_nav_host_fragment) as? NavHostFragment
            ?: (binding.homeNavHostFragment.getFragment<NavHostFragment>())

        childNavController = navHostFragment.navController

        // Setup toolbar with drawer toggle
        toggle = ActionBarDrawerToggle(
            requireActivity(),
            binding.drawerLayout,
            binding.toolbar,
            R.string.nav_drawer_open,
            R.string.nav_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Setup NavigationView with NavController
        childNavController?.let { nav ->
            NavigationUI.setupWithNavController(binding.navView, nav)

            nav.addOnDestinationChangedListener { _, destination, _ ->
                binding.toolbar.title = destination.label
            }
        }

        // Setup header info
        val repository = NetworkRepository(requireContext())
        val savedCredentials = repository.getSavedCredentials()
        val headerView = binding.navView.getHeaderView(0)
        val subtitleView = headerView.findViewById<TextView>(R.id.nav_header_subtitle)
        subtitleView.text = savedCredentials?.ipAddress ?: ""

        // Add logout item handling
        binding.navView.menu.findItem(R.id.notificationSettingsFragment)?.let {
            // Already handled by NavigationUI
        }

        // Add logout to toolbar menu
        binding.toolbar.inflateMenu(R.menu.menu_home)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_logout -> {
                    performLogout()
                    true
                }
                else -> false
            }
        }
    }

    private fun performLogout() {
        CoroutineScope(Dispatchers.Main).launch {
            LuciApiClient.logout()
            val repository = NetworkRepository(requireContext())
            repository.logout()
            findNavController().navigate(R.id.action_home_to_login)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
