package com.example.lucimanager.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.lucimanager.model.*
import com.example.lucimanager.network.LuciApiClient

class NetworkRepository(private val context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs()
    private val monitorPrefs: SharedPreferences = context.getSharedPreferences("monitoring_prefs", Context.MODE_PRIVATE)

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

    // ==================== AUTH ====================

    suspend fun login(credentials: LoginCredentials): Result<LuciSession> {
        val result = LuciApiClient.login(credentials)
        if (result.isSuccess) {
            saveCredentials(credentials)
        }
        return result
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

    // ==================== NETWORK INTERFACES ====================

    suspend fun getNetworkInterfaces(): Result<List<NetworkInterface>> {
        return LuciApiClient.getNetworkInterfaces()
    }

    suspend fun toggleInterface(interfaceName: String, enable: Boolean): Result<Boolean> {
        return LuciApiClient.toggleInterface(interfaceName, enable)
    }

    // ==================== DASHBOARD ====================

    suspend fun getRouterInfo(): Result<RouterInfo> {
        return LuciApiClient.getRouterInfo()
    }

    // ==================== WIFI ====================

    suspend fun getWifiNetworks(): Result<List<WifiNetwork>> {
        return LuciApiClient.getWifiNetworks()
    }

    suspend fun updateWifiNetwork(network: WifiNetwork): Result<Boolean> {
        return LuciApiClient.updateWifiNetwork(network)
    }

    // ==================== DEVICES ====================

    suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        return LuciApiClient.getConnectedDevices()
    }

    // ==================== VPN ====================

    suspend fun getVpnConnections(): Result<List<VpnConnection>> {
        return LuciApiClient.getVpnConnections()
    }

    suspend fun toggleVpn(vpn: VpnConnection, enable: Boolean): Result<Boolean> {
        return LuciApiClient.toggleVpn(vpn, enable)
    }

    // ==================== PACKAGES ====================

    suspend fun getInstalledPackages(): Result<List<OpkgPackage>> {
        return LuciApiClient.getInstalledPackages()
    }

    suspend fun searchPackages(query: String): Result<List<OpkgPackage>> {
        return LuciApiClient.searchPackages(query)
    }

    suspend fun installPackage(packageName: String): Result<Boolean> {
        return LuciApiClient.installPackage(packageName)
    }

    suspend fun removePackage(packageName: String): Result<Boolean> {
        return LuciApiClient.removePackage(packageName)
    }

    suspend fun updatePackageLists(): Result<Boolean> {
        return LuciApiClient.updatePackageLists()
    }

    // ==================== SYSTEM ====================

    suspend fun rebootRouter(): Result<Boolean> {
        return LuciApiClient.rebootRouter()
    }

    suspend fun shutdownRouter(): Result<Boolean> {
        return LuciApiClient.shutdownRouter()
    }

    // ==================== MONITORING STATE ====================

    fun getMonitoringState(): MonitoringState {
        val isEnabled = monitorPrefs.getBoolean("monitoring_enabled", false)
        val interval = monitorPrefs.getInt("monitoring_interval", 15)
        val macsString = monitorPrefs.getString("previous_macs", "") ?: ""
        val macs = if (macsString.isBlank()) emptySet() else macsString.split(",").toSet()
        return MonitoringState(
            previousMacs = macs,
            isEnabled = isEnabled,
            intervalMinutes = interval
        )
    }

    fun saveMonitoringState(state: MonitoringState) {
        monitorPrefs.edit()
            .putBoolean("monitoring_enabled", state.isEnabled)
            .putInt("monitoring_interval", state.intervalMinutes)
            .putString("previous_macs", state.previousMacs.joinToString(","))
            .apply()
    }
}
