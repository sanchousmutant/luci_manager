package com.example.lucimanager.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

object NetworkUtils {
    
    /**
     * Check if device is connected to WiFi network
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }
    
    /**
     * Get current WiFi network SSID
     */
    fun getCurrentWifiSSID(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we need location permission to get SSID
            // or use NetworkCallback approach
            null // Would require additional permissions
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
        }
    }
    
    /**
     * Validate IP address format
     */
    fun isValidIPAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
    
    /**
     * Check if IP is in private range (for local router detection)
     */
    fun isPrivateIPAddress(ip: String): Boolean {
        if (!isValidIPAddress(ip)) return false
        
        val parts = ip.split(".").map { it.toInt() }
        
        // 192.168.x.x
        if (parts[0] == 192 && parts[1] == 168) return true
        
        // 10.x.x.x
        if (parts[0] == 10) return true
        
        // 172.16.x.x - 172.31.x.x
        if (parts[0] == 172 && parts[1] in 16..31) return true
        
        return false
    }
    
    /**
     * Generate common router IP addresses for suggestions
     */
    fun getCommonRouterIPs(): List<String> {
        return listOf(
            "192.168.1.1",
            "192.168.0.1",
            "192.168.1.254", 
            "192.168.0.254",
            "10.0.0.1",
            "172.16.1.1"
        )
    }
}