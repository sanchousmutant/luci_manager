package com.example.lucimanager.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.lucimanager.model.LoginCredentials
import com.example.lucimanager.model.LuciSession
import com.example.lucimanager.model.NetworkInterface
import com.example.lucimanager.network.LuciApiClient
import com.google.gson.Gson

class NetworkRepository(private val context: Context) {
    private val apiClient = LuciApiClient()
    private val prefs: SharedPreferences = context.getSharedPreferences("luci_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun login(credentials: LoginCredentials): Result<LuciSession> {
        val result = apiClient.login(credentials)
        if (result.isSuccess) {
            // Save credentials securely (in a real app, consider using encrypted storage)
            saveCredentials(credentials)
        }
        return result
    }

    suspend fun getNetworkInterfaces(): Result<List<NetworkInterface>> {
        return apiClient.getNetworkInterfaces()
    }

    suspend fun toggleInterface(interfaceName: String, enable: Boolean): Result<Boolean> {
        return apiClient.toggleInterface(interfaceName, enable)
    }

    suspend fun logout(): Result<Boolean> {
        val result = apiClient.logout()
        clearSavedCredentials()
        return result
    }

    fun getSavedCredentials(): LoginCredentials? {
        val ip = prefs.getString("ip_address", null)
        val username = prefs.getString("username", null)
        val password = prefs.getString("password", null)
        
        return if (ip != null && username != null && password != null) {
            LoginCredentials(ip, username, password)
        } else null
    }

    private fun saveCredentials(credentials: LoginCredentials) {
        prefs.edit()
            .putString("ip_address", credentials.ipAddress)
            .putString("username", credentials.username)
            .putString("password", credentials.password)
            .apply()
    }

    private fun clearSavedCredentials() {
        prefs.edit()
            .remove("ip_address")
            .remove("username")
            .remove("password")
            .apply()
    }
}