package com.example.lucimanager.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.lucimanager.model.LoginCredentials
import com.example.lucimanager.model.LuciSession
import com.example.lucimanager.model.NetworkInterface
import com.example.lucimanager.network.LuciApiClient

class NetworkRepository(private val context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs()

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "luci_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("luci_prefs", Context.MODE_PRIVATE)
        }
    }

    suspend fun login(credentials: LoginCredentials): Result<LuciSession> {
        val result = LuciApiClient.login(credentials)
        if (result.isSuccess) {
            saveCredentials(credentials)
        }
        return result
    }

    suspend fun getNetworkInterfaces(): Result<List<NetworkInterface>> {
        return LuciApiClient.getNetworkInterfaces()
    }

    suspend fun toggleInterface(interfaceName: String, enable: Boolean): Result<Boolean> {
        return LuciApiClient.toggleInterface(interfaceName, enable)
    }

    suspend fun logout(): Result<Boolean> {
        val result = LuciApiClient.logout()
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
