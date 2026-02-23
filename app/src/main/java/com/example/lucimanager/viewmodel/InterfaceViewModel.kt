package com.example.lucimanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.lucimanager.model.NetworkInterface
import com.example.lucimanager.repository.NetworkRepository
import kotlinx.coroutines.launch

sealed class InterfaceState {
    object Loading : InterfaceState()
    data class Success(val interfaces: List<NetworkInterface>) : InterfaceState()
    data class Error(val message: String) : InterfaceState()
}

sealed class ToggleState {
    object Idle : ToggleState()
    data class Loading(val interfaceName: String) : ToggleState()
    data class Success(val interfaceName: String, val enabled: Boolean) : ToggleState()
    data class Error(val message: String) : ToggleState()
}

class InterfaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NetworkRepository(application)
    
    private val _interfaceState = MutableLiveData<InterfaceState>()
    val interfaceState: LiveData<InterfaceState> = _interfaceState

    private val _toggleState = MutableLiveData<ToggleState>(ToggleState.Idle)
    val toggleState: LiveData<ToggleState> = _toggleState

    fun loadInterfaces() {
        _interfaceState.value = InterfaceState.Loading
        
        viewModelScope.launch {
            val result = repository.getNetworkInterfaces()
            
            _interfaceState.value = if (result.isSuccess) {
                InterfaceState.Success(result.getOrThrow())
            } else {
                InterfaceState.Error(result.exceptionOrNull()?.message ?: "Failed to load interfaces")
            }
        }
    }

    fun toggleInterface(interfaceName: String, enable: Boolean) {
        _toggleState.value = ToggleState.Loading(interfaceName)
        
        viewModelScope.launch {
            val result = repository.toggleInterface(interfaceName, enable)
            
            _toggleState.value = if (result.isSuccess) {
                ToggleState.Success(interfaceName, enable)
                // Reload interfaces to get updated status
                loadInterfaces()
                ToggleState.Idle
            } else {
                ToggleState.Error(result.exceptionOrNull()?.message ?: "Failed to toggle interface")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun clearToggleState() {
        _toggleState.value = ToggleState.Idle
    }
}