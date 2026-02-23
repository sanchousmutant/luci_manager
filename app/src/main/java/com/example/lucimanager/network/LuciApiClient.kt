package com.example.lucimanager.network

import com.example.lucimanager.BuildConfig
import com.example.lucimanager.model.LoginCredentials
import com.example.lucimanager.model.LuciSession
import com.example.lucimanager.model.NetworkInterface
import com.google.gson.JsonArray
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

object LuciApiClient {
    private var currentSession: LuciSession? = null
    private var requestId = 0

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
            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor { message ->
                    println("LuciApiClient: $message")
                }
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                addNetworkInterceptor(loggingInterceptor)
            }
        }
        .build()

    private fun getUbusUrl(ipAddress: String): String {
        return "http://$ipAddress/cgi-bin/luci/admin/ubus"
    }

    private fun createUbusRequest(sessionToken: String, ubusObject: String, ubusMethod: String, args: JsonObject = JsonObject()): RequestBody {
        requestId++
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", requestId)
            addProperty("method", "call")
            add("params", JsonArray().apply {
                add(sessionToken)
                add(ubusObject)
                add(ubusMethod)
                add(args)
            })
        }
        return request.toString().toRequestBody("application/json".toMediaType())
    }

    private fun parseUbusResponse(body: String): Pair<Int, JsonObject?> {
        val json = JsonParser.parseString(body).asJsonObject
        val error = json.get("error")
        if (error != null && !error.isJsonNull) {
            throw Exception("JSON-RPC error: $error")
        }
        val result = json.getAsJsonArray("result")
        val code = result[0].asInt
        val data = if (result.size() > 1 && result[1].isJsonObject) result[1].asJsonObject else null
        return Pair(code, data)
    }

    private fun ubusErrorMessage(code: Int): String = when (code) {
        0 -> "Success"
        1 -> "Invalid command"
        2 -> "Invalid argument"
        3 -> "Method not found"
        4 -> "Not found"
        5 -> "No data"
        6 -> "Permission denied"
        7 -> "Timeout"
        else -> "Unknown error (code $code)"
    }

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

            val args = JsonObject().apply {
                addProperty("username", credentials.username)
                addProperty("password", credentials.password)
            }

            val ubusBody = createUbusRequest(
                sessionToken = "00000000000000000000000000000000",
                ubusObject = "session",
                ubusMethod = "login",
                args = args
            )

            val request = Request.Builder()
                .url(getUbusUrl(cleanIpAddress))
                .post(ubusBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code == 404 -> {
                    Result.failure(Exception("ubus endpoint not found. Verify router IP address and that LuCI is enabled."))
                }
                response.code >= 400 -> {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        try {
                            val (code, data) = parseUbusResponse(body)

                            if (code == 6) {
                                return@withContext Result.failure(Exception("Authentication failed: Invalid credentials"))
                            }
                            if (code != 0) {
                                return@withContext Result.failure(Exception("Login failed: ${ubusErrorMessage(code)}"))
                            }

                            val ubusToken = data?.get("ubus_rpc_session")?.asString
                                ?: return@withContext Result.failure(Exception("No session token in response"))

                            val expires = data.get("expires")?.asInt ?: (5 * 60)

                            val session = LuciSession(
                                token = ubusToken,
                                ipAddress = cleanIpAddress,
                                username = credentials.username,
                                isValid = true,
                                createdAt = System.currentTimeMillis(),
                                expiresAt = System.currentTimeMillis() + (expires * 1000L)
                            )
                            currentSession = session
                            Result.success(session)
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
            Result.failure(Exception("Network error: ${e.message}\nPossible causes:\n• Firewall blocking\n• Incorrect IP address\n• Router LuCI service not running"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message}"))
        }
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

        try {
            val ubusBody = createUbusRequest(
                sessionToken = session.token,
                ubusObject = "network.interface",
                ubusMethod = "dump"
            )

            val request = Request.Builder()
                .url(getUbusUrl(session.ipAddress))
                .post(ubusBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code == 401 -> {
                    currentSession = null
                    Result.failure(Exception("Unauthorized: Session may have expired. Please log in again."))
                }
                response.code == 404 -> {
                    Result.failure(Exception("Network interface endpoint not found. Check if router supports ubus API."))
                }
                response.code >= 400 -> {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        try {
                            val (code, data) = parseUbusResponse(body)

                            if (code == 6) {
                                currentSession = null
                                return@withContext Result.failure(Exception("Permission denied. Session may have expired. Please log in again."))
                            }
                            if (code != 0) {
                                return@withContext Result.failure(Exception("Failed to get interfaces: ${ubusErrorMessage(code)}"))
                            }
                            if (data == null) {
                                return@withContext Result.success(emptyList())
                            }

                            parseNetworkInterfaces(data)
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

    private fun parseNetworkInterfaces(data: JsonObject): Result<List<NetworkInterface>> {
        val interfaces = mutableListOf<NetworkInterface>()
        val interfaceArray = data.getAsJsonArray("interface") ?: return Result.success(emptyList())

        for (element in interfaceArray) {
            val obj = element.asJsonObject
            val name = obj.get("interface")?.asString ?: continue
            val isActive = obj.get("up")?.asBoolean ?: false
            val protocol = obj.get("proto")?.asString
            val device = obj.get("device")?.asString
            val ipAddress = extractIpAddress(obj)
            val displayName = when (name) {
                "lan" -> "LAN"
                "wan" -> "WAN"
                "wwan" -> "WWAN"
                "guest" -> "Guest WiFi"
                else -> name.replaceFirstChar { it.uppercase() }
            }
            interfaces.add(NetworkInterface(name, displayName, isActive, protocol, ipAddress, device))
        }
        return Result.success(interfaces)
    }

    private fun extractIpAddress(obj: JsonObject): String? {
        val ipv4 = obj.getAsJsonArray("ipv4-address")
        if (ipv4 != null && ipv4.size() > 0) {
            return ipv4[0].asJsonObject.get("address")?.asString
        }
        val ipv6 = obj.getAsJsonArray("ipv6-address")
        if (ipv6 != null && ipv6.size() > 0) {
            return ipv6[0].asJsonObject.get("address")?.asString
        }
        return null
    }

    suspend fun toggleInterface(interfaceName: String, enable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val session = validateSession()

        if (interfaceName.isBlank()) {
            return@withContext Result.failure(Exception("Interface name is required"))
        }

        val action = if (enable) "up" else "down"

        try {
            val ubusBody = createUbusRequest(
                sessionToken = session.token,
                ubusObject = "network.interface.$interfaceName",
                ubusMethod = action
            )

            val request = Request.Builder()
                .url(getUbusUrl(session.ipAddress))
                .post(ubusBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.code == 401 -> {
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
                        try {
                            val (code, _) = parseUbusResponse(body)

                            if (code == 6) {
                                currentSession = null
                                return@withContext Result.failure(Exception("Permission denied. Session may have expired."))
                            }
                            if (code != 0) {
                                return@withContext Result.failure(Exception("Failed to toggle interface: ${ubusErrorMessage(code)}"))
                            }

                            Result.success(true)
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
            val args = JsonObject().apply {
                addProperty("ubus_rpc_session", session.token)
            }

            val ubusBody = createUbusRequest(
                sessionToken = session.token,
                ubusObject = "session",
                ubusMethod = "destroy",
                args = args
            )

            val request = Request.Builder()
                .url(getUbusUrl(session.ipAddress))
                .post(ubusBody)
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
