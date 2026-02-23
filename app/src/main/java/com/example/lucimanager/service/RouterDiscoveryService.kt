package com.example.lucimanager.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.example.lucimanager.model.DiscoveredRouter
import com.example.lucimanager.network.LuciApiClient
import kotlinx.coroutines.*
import okhttp3.Request
import java.net.Inet4Address

class RouterDiscoveryService(private val context: Context) {

    suspend fun discoverRouters(): List<DiscoveredRouter> = withContext(Dispatchers.IO) {
        val candidates = mutableSetOf<String>()

        // Get gateway IP
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            linkProperties?.routes?.forEach { route ->
                route.gateway?.let { gateway ->
                    if (gateway is Inet4Address && !gateway.isLoopbackAddress) {
                        candidates.add(gateway.hostAddress ?: return@let)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Add common defaults
        candidates.add("192.168.1.1")
        candidates.add("192.168.0.1")
        candidates.add("192.168.1.254")
        candidates.add("10.0.0.1")

        // Check each candidate in parallel
        val results = candidates.map { ip ->
            async {
                checkRouter(ip)
            }
        }

        results.mapNotNull { it.await() }.sortedBy { it.responseTime }
    }

    private suspend fun checkRouter(ip: String): DiscoveredRouter? {
        return try {
            val start = System.currentTimeMillis()
            val client = LuciApiClient.okHttpClient.newBuilder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://$ip/cgi-bin/luci/admin/ubus")
                .head()
                .build()

            val response = client.newCall(request).execute()
            val elapsed = System.currentTimeMillis() - start
            response.close()

            if (response.code in 200..499) {
                DiscoveredRouter(
                    ipAddress = ip,
                    hostname = ip,
                    responseTime = elapsed
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
