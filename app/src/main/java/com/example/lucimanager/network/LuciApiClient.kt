package com.example.lucimanager.network

import com.example.lucimanager.model.LoginCredentials
import com.example.lucimanager.model.LuciSession
import com.example.lucimanager.model.NetworkInterface
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class LuciApiClient {
    private val gson = Gson()
    private var currentSession: LuciSession? = null

    // Method to check if the current session is valid
    fun isSessionValid(): Boolean {
        return currentSession?.let {
            !it.isExpired() && it.isValid
        } ?: false
    }

    // Method to get current session if valid
    fun getCurrentSession(): LuciSession? {
        return if (isSessionValid()) currentSession else null
    }

    // Trust manager for self-signed certificates
    private val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    private fun createTrustAllSslContext(): SSLContext {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext
    }

    // Create OkHttp client that accepts self-signed certificates
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .hostnameVerifier { _, _ -> true }
        .sslSocketFactory(createTrustAllSslContext().socketFactory, trustAllCerts[0] as X509TrustManager)
        .apply {
            // Add logging interceptor for debugging
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                println("LuciApiClient: $message") // Simple logging
            }
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            addNetworkInterceptor(loggingInterceptor)
        }
        .build()

    suspend fun login(credentials: LoginCredentials): Result<LuciSession> = withContext(Dispatchers.IO) {
        // Validate input credentials
        if (credentials.ipAddress.isBlank()) {
            return@withContext Result.failure(Exception("Router IP address is required"))
        }
        if (credentials.username.isBlank()) {
            return@withContext Result.failure(Exception("Username is required"))
        }
        if (credentials.password.isBlank()) {
            return@withContext Result.failure(Exception("Password is required"))
        }

        try {
            // Clean IP address
            val cleanIpAddress = credentials.ipAddress.trim().removePrefix("http://").removePrefix("https://")

            // Perform login via RPC
            val loginRequest = createLoginRequest(credentials)
            val request = Request.Builder()
                .url("http://$cleanIpAddress/cgi-bin/luci/rpc/auth")
                .post(loginRequest)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code == 401 -> {
                    Result.failure(Exception("Authentication failed: Invalid credentials"))
                }
                response.code == 404 -> {
                    Result.failure(Exception("Authentication endpoint not found. Verify router IP address and that LuCI is enabled."))
                }
                response.code >= 400 -> {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        // Check if the response is valid JSON
                        try {
                            val jsonResponse = JsonParser.parseString(body).asJsonObject
                            val error = jsonResponse.get("error")
                            if (error != null && !error.isJsonNull) {
                                val errorMessage = error.asString
                                Result.failure(Exception("API Error: $errorMessage"))
                            } else {
                                val result = jsonResponse.get("result")
                                if (result != null && !result.isJsonNull) {
                                    val token = result.asString
                                    val sessionId = extractSessionId(response, cleanIpAddress)

                                    // Create session with a reasonable expiration time
                                    val session = LuciSession(
                                        token = token,
                                        sessionId = sessionId,
                                        ipAddress = cleanIpAddress,
                                        username = credentials.username,
                                        isValid = true,
                                        createdAt = System.currentTimeMillis(),
                                        expiresAt = System.currentTimeMillis() + (30 * 60 * 1000) // 30 minutes default
                                    )
                                    currentSession = session
                                    Result.success(session)
                                } else {
                                    Result.failure(Exception("Invalid credentials or authentication failed"))
                                }
                            }
                        } catch (jsonEx: JsonSyntaxException) {
                            Result.failure(Exception("Invalid JSON response from server: ${jsonEx.message}"))
                        }
                    } ?: Result.failure(Exception("Empty response body from server"))
                }
                else -> {
                    Result.failure(Exception("Unexpected response from server: ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("Cannot connect to router. Please check IP address (${credentials.ipAddress}) and network connection."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out. Please check if the router is accessible at ${credentials.ipAddress}."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Host not found. Please check the router IP address: ${credentials.ipAddress}"))
        } catch (e: IOException) {
            // Handle specific network errors with detailed messages
            when (e) {
                is ConnectException -> {
                    Result.failure(Exception("Cannot connect to router. Please check:\n• IP address: ${credentials.ipAddress}\n• Network connection\n• Router is online and LuCI is enabled"))
                }
                is SocketTimeoutException -> {
                    Result.failure(Exception("Connection timed out. Please check:\n• Router at ${credentials.ipAddress} is accessible\n• Network connection quality"))
                }
                is UnknownHostException -> {
                    Result.failure(Exception("Host not found. Please check the router IP address: ${credentials.ipAddress}"))
                }
                else -> {
                    Result.failure(Exception("Network error: ${e.message}\nPossible causes:\n• Firewall blocking\n• Incorrect IP address\n• Router LuCI service not running"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
    }

    private fun createLoginRequest(credentials: LoginCredentials): RequestBody {
        val loginData = JsonObject().apply {
            addProperty("id", 1)
            addProperty("method", "login")
            add("params", gson.toJsonTree(arrayOf(credentials.username, credentials.password)))
        }

        return loginData.toString().toRequestBody("application/json".toMediaType())
    }

    private fun getBaseUrl(ipAddress: String): String {
        // Check if the IP address already includes the protocol
        return if (ipAddress.startsWith("http://") || ipAddress.startsWith("https://")) {
            ipAddress
        } else {
            "http://$ipAddress"
        }
    }

    private fun extractSessionId(response: Response, ipAddress: String): String {
        val cookies = response.headers("Set-Cookie")
        for (cookie in cookies) {
            if (cookie.contains("sysauth=")) {
                val parts = cookie.split(";")[0].split("=")
                if (parts.size > 1) {
                    return parts[1]
                }
            }
        }
        return ""
    }

    // Helper function to validate the current session and return a valid session or throw an exception
    private fun validateSession(): LuciSession {
        val session = currentSession
        if (session == null) {
            throw Exception("Not logged in")
        }

        if (session.isExpired()) {
            currentSession = null
            throw Exception("Session has expired. Please log in again.")
        }

        return session
    }

    suspend fun getNetworkInterfaces(): Result<List<NetworkInterface>> = withContext(Dispatchers.IO) {
        val session = validateSession()
        if (!session.isValidSession()) {
            return@withContext Result.failure(Exception("Not logged in or session expired"))
        }

        try {
            // Use the proper RPC method for getting interface information
            val rpcRequest = JsonObject().apply {
                addProperty("id", 2)
                addProperty("method", "call")
                add("params", gson.toJsonTree(arrayOf(session.token, "network.interface", "dump")))
            }

            val request = Request.Builder()
                .url("http://${session.ipAddress}/cgi-bin/luci/rpc/sys")
                .post(rpcRequest.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code == 401 -> {
                    // Session expired on the server, clear our local session
                    currentSession = null
                    Result.failure(Exception("Unauthorized: Session may have expired. Please log in again."))
                }
                response.code == 404 -> {
                    Result.failure(Exception("Network interface endpoint not found. Check if router supports LuCI RPC API."))
                }
                response.code >= 400 -> {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        // Check if the response is valid JSON
                        try {
                            val jsonResponse = JsonParser.parseString(body).asJsonObject
                            val error = jsonResponse.get("error")
                            if (error != null && !error.isJsonNull) {
                                val errorMessage = error.asString
                                Result.failure(Exception("API Error: $errorMessage"))
                            } else {
                                parseNetworkInterfaces(body)
                            }
                        } catch (jsonEx: JsonSyntaxException) {
                            Result.failure(Exception("Invalid JSON response from server: ${jsonEx.message}"))
                        }
                    } ?: Result.failure(Exception("Empty response body from server"))
                }
                else -> {
                    Result.failure(Exception("Unexpected response from server: ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("Cannot connect to router at ${session.ipAddress}. Check network connection and LuCI service."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Request timed out while fetching interfaces from ${session.ipAddress}. Check network quality."))
        } catch (e: IOException) {
            // Handle other IO exceptions
            when (e) {
                is UnknownHostException -> {
                    Result.failure(Exception("Host not found: ${session.ipAddress}. Please check IP address."))
                }
                else -> {
                    Result.failure(Exception("Network error while fetching interfaces: ${e.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error while fetching interfaces: ${e.message}"))
        }
    }

    private fun parseNetworkInterfaces(jsonResponse: String): Result<List<NetworkInterface>> {
        try {
            val interfaces = mutableListOf<NetworkInterface>()
            val jsonElement = JsonParser.parseString(jsonResponse)

            if (jsonElement.isJsonObject) {
                val jsonObject = jsonElement.asJsonObject
                val result = jsonObject.get("result")

                if (result != null && result.isJsonObject) {
                    val resultObject = result.asJsonObject

                    // LuCI API returns interfaces as a JSON object with interface names as keys
                    for (entry in resultObject.entrySet()) {
                        val interfaceName = entry.key
                        val interfaceData = entry.value

                        if (interfaceData.isJsonObject) {
                            val interfaceObj = interfaceData.asJsonObject

                            // Extract relevant fields from the interface data
                            val isActive = interfaceObj.get("up")?.asBoolean ?: false
                            val protocol = interfaceObj.get("proto")?.asString
                            val ipAddress = getIpAddressFromInterface(interfaceObj)
                            val device = interfaceObj.get("device")?.asString

                            // Create a display name from the interface name if not available
                            val displayName = when (interfaceName) {
                                "lan" -> "LAN"
                                "wan" -> "WAN"
                                "wwan" -> "WWAN"
                                "guest" -> "Guest WiFi"
                                else -> interfaceName.replaceFirstChar { it.uppercase() }
                            }

                            interfaces.add(NetworkInterface(
                                name = interfaceName,
                                displayName = displayName,
                                isActive = isActive,
                                protocol = protocol,
                                ipAddress = ipAddress,
                                device = device
                            ))
                        }
                    }
                }
            }

            return Result.success(interfaces)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun getIpAddressFromInterface(interfaceObj: JsonObject): String? {
        // Try to get primary IP address from the interface data
        val inetEntry: JsonArray? = interfaceObj.get("inet6addr")?.asJsonArray
        if (inetEntry != null && inetEntry.size() > 0) {
            return inetEntry.get(0).asJsonObject.get("addr")?.getAsString()
        }

        // Check for IPv4 addresses
        val inet4addr: JsonElement? = interfaceObj.get("inet4addr")
        if (inet4addr != null && inet4addr.isJsonArray) {
            val jsonArray = inet4addr.asJsonArray
            if (jsonArray.size() > 0) {
                val addrObj = jsonArray.get(0).asJsonObject
                return addrObj.get("addr")?.getAsString()
            }
        }

        // Alternative field for IPv4
        val ipaddr: JsonElement? = interfaceObj.get("ipaddr")
        if (ipaddr != null && ipaddr.isJsonPrimitive) {
            return ipaddr.getAsString()
        }

        return null
    }

    suspend fun toggleInterface(interfaceName: String, enable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val session = validateSession()
        if (!session.isValidSession()) {
            return@withContext Result.failure(Exception("Not logged in or session expired"))
        }

        if (interfaceName.isBlank()) {
            return@withContext Result.failure(Exception("Interface name is required"))
        }

        val action = if (enable) "up" else "down"

        try {
            val rpcRequest = JsonObject().apply {
                addProperty("id", 3)
                addProperty("method", "call")
                add("params", gson.toJsonTree(arrayOf(session.token, "network.interface", action, interfaceName)))
            }

            val request = Request.Builder()
                .url("http://${session.ipAddress}/cgi-bin/luci/rpc/sys")
                .post(rpcRequest.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code == 401 -> {
                    // Session expired on the server, clear our local session
                    currentSession = null
                    Result.failure(Exception("Unauthorized: Session may have expired. Please log in again."))
                }
                response.code == 404 -> {
                    Result.failure(Exception("Interface '$interfaceName' not found or router doesn't support this operation"))
                }
                response.code >= 400 -> {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        // Check if the response is valid JSON and contains any errors
                        try {
                            val jsonResponse = JsonParser.parseString(body).asJsonObject
                            val error = jsonResponse.get("error")
                            if (error != null && !error.isJsonNull) {
                                val errorMessage = error.asString
                                Result.failure(Exception("API Error: $errorMessage"))
                            } else {
                                // The interface operation was successful
                                Result.success(true)
                            }
                        } catch (jsonEx: JsonSyntaxException) {
                            Result.failure(Exception("Invalid JSON response from server: ${jsonEx.message}"))
                        }
                    } ?: Result.failure(Exception("Empty response body from server"))
                }
                else -> {
                    Result.failure(Exception("Unexpected response from server: ${response.code}"))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("Cannot connect to router at ${session.ipAddress} while toggling interface."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Request timed out while toggling interface at ${session.ipAddress}."))
        } catch (e: Exception) {
            Result.failure(Exception("Network error occurred: ${e.message}"))
        }
    }

    suspend fun logout(): Result<Boolean> = withContext(Dispatchers.IO) {
        val session = currentSession
        if (session == null) {
            return@withContext Result.success(true)
        }

        try {
            val rpcRequest = JsonObject().apply {
                addProperty("id", 4)
                addProperty("method", "call")
                add("params", gson.toJsonTree(arrayOf(session.token, "session", "destroy", session.token)))
            }

            val request = Request.Builder()
                .url("http://${session.ipAddress}/cgi-bin/luci/rpc/sys")
                .post(rpcRequest.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code >= 400 -> {
                    // Even if logout fails on the server, clear the local session
                    currentSession = null
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    currentSession = null
                    Result.success(true)
                }
                else -> {
                    currentSession = null
                    Result.failure(Exception("Unexpected response from server: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            // Even if logout fails on the server, clear the local session
            currentSession = null
            Result.success(true) // Return success to allow UI cleanup even if server logout failed
        }
    }
}