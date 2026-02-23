package com.example.lucimanager.network

import com.example.lucimanager.BuildConfig
import com.example.lucimanager.model.*
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

    val okHttpClient = OkHttpClient.Builder()
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

    fun getCurrentSession(): LuciSession? = currentSession

    private suspend fun callUbus(ubusObject: String, ubusMethod: String, args: JsonObject = JsonObject()): Result<JsonObject?> = withContext(Dispatchers.IO) {
        val session = validateSession()
        try {
            val ubusBody = createUbusRequest(session.token, ubusObject, ubusMethod, args)
            val request = Request.Builder()
                .url(getUbusUrl(session.ipAddress))
                .post(ubusBody)
                .build()
            val response = okHttpClient.newCall(request).execute()
            when {
                response.code == 401 -> {
                    currentSession = null
                    Result.failure(Exception("Unauthorized: Session may have expired."))
                }
                response.code >= 400 -> {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                response.isSuccessful -> {
                    val responseBody = response.body?.string()
                    responseBody?.let { body ->
                        val (code, data) = parseUbusResponse(body)
                        if (code == 6) {
                            currentSession = null
                            return@withContext Result.failure(Exception("Permission denied."))
                        }
                        if (code != 0) {
                            return@withContext Result.failure(Exception(ubusErrorMessage(code)))
                        }
                        Result.success(data)
                    } ?: Result.failure(Exception("Empty response"))
                }
                else -> Result.failure(Exception("Unexpected response: ${response.code}"))
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("Cannot connect to router at ${session.ipAddress}."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Request timed out."))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Error: ${e.message}"))
        }
    }

    // ==================== LOGIN / LOGOUT ====================

    suspend fun login(credentials: LoginCredentials): Result<LuciSession> = withContext(Dispatchers.IO) {
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
                            Result.failure(Exception("Invalid JSON response: ${jsonEx.message}"))
                        }
                    } ?: Result.failure(Exception("Empty response body"))
                }
                else -> Result.failure(Exception("Unexpected response: ${response.code}"))
            }
        } catch (e: ConnectException) {
            Result.failure(Exception("Cannot connect to router at ${credentials.ipAddress}."))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Connection timed out to ${credentials.ipAddress}."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Host not found: ${credentials.ipAddress}"))
        } catch (e: IOException) {
            Result.failure(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unexpected error: ${e.message}"))
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
            val ubusBody = createUbusRequest(session.token, "session", "destroy", args)
            val request = Request.Builder()
                .url(getUbusUrl(session.ipAddress))
                .post(ubusBody)
                .build()
            okHttpClient.newCall(request).execute()
            currentSession = null
            Result.success(true)
        } catch (e: Exception) {
            currentSession = null
            Result.success(true)
        }
    }

    // ==================== NETWORK INTERFACES ====================

    suspend fun getNetworkInterfaces(): Result<List<NetworkInterface>> = withContext(Dispatchers.IO) {
        val result = callUbus("network.interface", "dump")
        result.fold(
            onSuccess = { data ->
                if (data == null) return@withContext Result.success(emptyList())
                parseNetworkInterfaces(data)
            },
            onFailure = { Result.failure(it) }
        )
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
        val action = if (enable) "up" else "down"
        val result = callUbus("network.interface.$interfaceName", action)
        result.fold(
            onSuccess = { Result.success(true) },
            onFailure = { Result.failure(it) }
        )
    }

    // ==================== SYSTEM INFO (Dashboard) ====================

    suspend fun getSystemBoard(): Result<JsonObject?> = callUbus("system", "board")

    suspend fun getSystemInfo(): Result<JsonObject?> = callUbus("system", "info")

    suspend fun getRouterInfo(): Result<RouterInfo> = withContext(Dispatchers.IO) {
        try {
            val boardResult = getSystemBoard()
            val infoResult = getSystemInfo()

            val board = boardResult.getOrNull()
            val info = infoResult.getOrNull()

            if (board == null && info == null) {
                return@withContext Result.failure(
                    boardResult.exceptionOrNull() ?: Exception("Failed to get system info")
                )
            }

            val hostname = board?.get("hostname")?.asString ?: ""
            val model = board?.get("model")?.asString ?: ""
            val release = board?.getAsJsonObject("release")
            val firmwareVersion = release?.get("description")?.asString ?: ""
            val kernelVersion = board?.get("kernel")?.asString ?: ""

            val uptime = info?.get("uptime")?.asLong ?: 0
            val memory = info?.getAsJsonObject("memory")
            val memTotal = memory?.get("total")?.asLong ?: 0
            val memFree = memory?.get("free")?.asLong ?: 0
            val memBuffered = memory?.get("buffered")?.asLong ?: 0

            val loadArray = info?.getAsJsonArray("load")
            val loadAvg = if (loadArray != null && loadArray.size() >= 3) {
                listOf(
                    loadArray[0].asDouble / 65536.0,
                    loadArray[1].asDouble / 65536.0,
                    loadArray[2].asDouble / 65536.0
                )
            } else {
                listOf(0.0, 0.0, 0.0)
            }

            Result.success(
                RouterInfo(
                    hostname = hostname,
                    model = model,
                    firmwareVersion = firmwareVersion,
                    kernelVersion = kernelVersion,
                    uptime = uptime,
                    memTotal = memTotal,
                    memFree = memFree,
                    memBuffered = memBuffered,
                    loadAvg = loadAvg
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== WIFI ====================

    suspend fun getWirelessStatus(): Result<JsonObject?> = callUbus("network.wireless", "status")

    suspend fun getUciConfig(config: String): Result<JsonObject?> {
        val args = JsonObject().apply { addProperty("config", config) }
        return callUbus("uci", "get", args)
    }

    suspend fun setUciOption(config: String, section: String, option: String, value: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val args = JsonObject().apply {
            addProperty("config", config)
            addProperty("section", section)
            add("values", JsonObject().apply {
                addProperty(option, value)
            })
        }
        val result = callUbus("uci", "set", args)
        result.fold(
            onSuccess = { Result.success(true) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun setUciOptions(config: String, section: String, values: Map<String, String>): Result<Boolean> = withContext(Dispatchers.IO) {
        val args = JsonObject().apply {
            addProperty("config", config)
            addProperty("section", section)
            add("values", JsonObject().apply {
                values.forEach { (k, v) -> addProperty(k, v) }
            })
        }
        val result = callUbus("uci", "set", args)
        result.fold(
            onSuccess = { Result.success(true) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun uciCommit(config: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val args = JsonObject().apply { addProperty("config", config) }
        val result = callUbus("uci", "commit", args)
        result.fold(
            onSuccess = { Result.success(true) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun execCommand(command: String, params: List<String> = emptyList()): Result<JsonObject?> {
        val args = JsonObject().apply {
            addProperty("command", command)
            if (params.isNotEmpty()) {
                add("params", JsonArray().apply { params.forEach { add(it) } })
            }
        }
        return callUbus("file", "exec", args)
    }

    suspend fun getWifiNetworks(): Result<List<WifiNetwork>> = withContext(Dispatchers.IO) {
        try {
            val statusResult = getWirelessStatus()
            val uciResult = getUciConfig("wireless")

            val networks = mutableListOf<WifiNetwork>()
            val uciData = uciResult.getOrNull()
            val statusData = statusResult.getOrNull()

            val uciValues = uciData?.getAsJsonObject("values")

            if (uciValues != null) {
                for (key in uciValues.keySet()) {
                    val section = uciValues.getAsJsonObject(key) ?: continue
                    val sectionType = section.get(".type")?.asString ?: ""
                    if (sectionType != "wifi-iface") continue

                    val radioName = section.get("device")?.asString ?: ""
                    val ifname = section.get("ifname")?.asString ?: key
                    val ssid = section.get("ssid")?.asString ?: ""
                    val password = section.get("key")?.asString ?: ""
                    val mode = section.get("mode")?.asString ?: "ap"
                    val encryption = section.get("encryption")?.asString ?: ""
                    val disabled = section.get("disabled")?.asString ?: "0"

                    // Get radio info for channel/frequency
                    var channel = "auto"
                    var txPower = ""
                    var frequency = ""
                    if (radioName.isNotEmpty()) {
                        for (radioKey in uciValues.keySet()) {
                            val radioSection = uciValues.getAsJsonObject(radioKey) ?: continue
                            if (radioSection.get(".type")?.asString == "wifi-device" &&
                                radioSection.get(".name")?.asString == radioName) {
                                channel = radioSection.get("channel")?.asString ?: "auto"
                                txPower = radioSection.get("txpower")?.asString ?: ""
                                val band = radioSection.get("band")?.asString ?: ""
                                frequency = when {
                                    band.contains("5") -> "5 GHz"
                                    band.contains("2") -> "2.4 GHz"
                                    else -> band
                                }
                                break
                            }
                        }
                    }

                    networks.add(
                        WifiNetwork(
                            radioName = radioName,
                            ifname = ifname,
                            ssid = ssid,
                            password = password,
                            channel = channel,
                            txPower = txPower,
                            enabled = disabled != "1",
                            mode = mode,
                            encryption = encryption,
                            frequency = frequency,
                            sectionName = section.get(".name")?.asString ?: key
                        )
                    )
                }
            }

            Result.success(networks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWifiNetwork(network: WifiNetwork): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val values = mutableMapOf<String, String>()
            values["ssid"] = network.ssid
            if (network.password.isNotEmpty()) {
                values["key"] = network.password
            }
            val setResult = setUciOptions("wireless", network.sectionName, values)
            if (setResult.isFailure) return@withContext setResult

            val commitResult = uciCommit("wireless")
            if (commitResult.isFailure) return@withContext commitResult

            execCommand("wifi", listOf("reload"))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== CONNECTED DEVICES ====================

    suspend fun getDHCPLeases(): Result<JsonObject?> = callUbus("luci-rpc", "getDHCPLeases")

    suspend fun getWifiClients(iface: String): Result<JsonObject?> = callUbus("hostapd.$iface", "get_clients")

    suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> = withContext(Dispatchers.IO) {
        try {
            val devices = mutableMapOf<String, ConnectedDevice>()

            // Get DHCP leases
            val leasesResult = getDHCPLeases()
            leasesResult.getOrNull()?.let { data ->
                val dhcpLeases = data.getAsJsonArray("dhcp_leases")
                dhcpLeases?.forEach { element ->
                    val lease = element.asJsonObject
                    val mac = lease.get("macaddr")?.asString?.uppercase() ?: return@forEach
                    val ip = lease.get("ipaddr")?.asString ?: ""
                    val hostname = lease.get("hostname")?.asString ?: ""
                    devices[mac] = ConnectedDevice(
                        mac = mac,
                        ip = ip,
                        hostname = hostname,
                        isWifi = false
                    )
                }
            }

            // Try to get WiFi clients from wireless status
            val wirelessResult = getWirelessStatus()
            wirelessResult.getOrNull()?.let { statusData ->
                for (radioKey in statusData.keySet()) {
                    val radio = statusData.getAsJsonObject(radioKey) ?: continue
                    val interfaces = radio.getAsJsonArray("interfaces") ?: continue
                    for (ifElement in interfaces) {
                        val iface = ifElement.asJsonObject
                        val ifname = iface.get("ifname")?.asString ?: continue
                        val clientsResult = getWifiClients(ifname)
                        clientsResult.getOrNull()?.let { clientData ->
                            val clients = clientData.getAsJsonObject("clients")
                            clients?.keySet()?.forEach { clientMac ->
                                val mac = clientMac.uppercase()
                                val clientInfo = clients.getAsJsonObject(clientMac)
                                val signal = clientInfo?.getAsJsonObject("signal")?.asInt ?: 0
                                val rxBytes = clientInfo?.get("rx_bytes")?.asLong ?: 0
                                val txBytes = clientInfo?.get("tx_bytes")?.asLong ?: 0
                                val existing = devices[mac]
                                devices[mac] = (existing ?: ConnectedDevice(mac = mac)).copy(
                                    isWifi = true,
                                    signal = signal,
                                    rxBytes = rxBytes,
                                    txBytes = txBytes
                                )
                            }
                        }
                    }
                }
            }

            Result.success(devices.values.toList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== VPN ====================

    suspend fun getServiceList(serviceName: String): Result<JsonObject?> {
        val args = JsonObject().apply { addProperty("name", serviceName) }
        return callUbus("service", "list", args)
    }

    suspend fun getVpnConnections(): Result<List<VpnConnection>> = withContext(Dispatchers.IO) {
        try {
            val vpns = mutableListOf<VpnConnection>()

            // Check OpenVPN
            val openvpnResult = getServiceList("openvpn")
            openvpnResult.getOrNull()?.let { data ->
                val openvpn = data.getAsJsonObject("openvpn")
                val instances = openvpn?.getAsJsonObject("instances")
                instances?.keySet()?.forEach { name ->
                    val instance = instances.getAsJsonObject(name)
                    val running = instance?.get("running")?.asBoolean ?: false
                    vpns.add(
                        VpnConnection(
                            name = name,
                            type = VpnType.OPENVPN,
                            isActive = running,
                            interfaceName = name
                        )
                    )
                }
            }

            // Check WireGuard interfaces from network config
            val netResult = getUciConfig("network")
            netResult.getOrNull()?.let { data ->
                val values = data.getAsJsonObject("values")
                values?.keySet()?.forEach { key ->
                    val section = values.getAsJsonObject(key) ?: return@forEach
                    val proto = section.get("proto")?.asString ?: ""
                    if (proto == "wireguard") {
                        val name = section.get(".name")?.asString ?: key
                        vpns.add(
                            VpnConnection(
                                name = name,
                                type = VpnType.WIREGUARD,
                                isActive = false,
                                interfaceName = name
                            )
                        )
                    }
                }
            }

            // Check active state of WireGuard via network.interface dump
            val ifacesResult = getNetworkInterfaces()
            ifacesResult.getOrNull()?.let { ifaces ->
                for (vpn in vpns) {
                    if (vpn.type == VpnType.WIREGUARD) {
                        val ifaceUp = ifaces.any { it.name == vpn.interfaceName && it.isActive }
                        val idx = vpns.indexOf(vpn)
                        if (idx >= 0) {
                            vpns[idx] = vpn.copy(isActive = ifaceUp)
                        }
                    }
                }
            }

            Result.success(vpns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleVpn(vpn: VpnConnection, enable: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            when (vpn.type) {
                VpnType.OPENVPN -> {
                    val action = if (enable) "start" else "stop"
                    execCommand("/etc/init.d/openvpn", listOf(action))
                }
                VpnType.WIREGUARD -> {
                    val method = if (enable) "up" else "down"
                    callUbus("network.interface.${vpn.interfaceName}", method)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== PACKAGES ====================

    suspend fun getInstalledPackages(): Result<List<OpkgPackage>> = withContext(Dispatchers.IO) {
        try {
            val result = execCommand("opkg", listOf("list-installed"))
            val data = result.getOrNull()
            val stdout = data?.get("stdout")?.asString ?: ""
            val packages = stdout.lines().filter { it.isNotBlank() }.map { line ->
                val parts = line.split(" - ", limit = 2)
                OpkgPackage(
                    name = parts.getOrElse(0) { "" }.trim(),
                    version = parts.getOrElse(1) { "" }.trim(),
                    isInstalled = true
                )
            }
            Result.success(packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchPackages(query: String): Result<List<OpkgPackage>> = withContext(Dispatchers.IO) {
        try {
            val result = execCommand("opkg", listOf("list"))
            val data = result.getOrNull()
            val stdout = data?.get("stdout")?.asString ?: ""
            val packages = stdout.lines().filter {
                it.isNotBlank() && it.contains(query, ignoreCase = true)
            }.map { line ->
                val parts = line.split(" - ", limit = 3)
                OpkgPackage(
                    name = parts.getOrElse(0) { "" }.trim(),
                    version = parts.getOrElse(1) { "" }.trim(),
                    description = parts.getOrElse(2) { "" }.trim(),
                    isInstalled = false
                )
            }
            Result.success(packages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installPackage(packageName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = execCommand("opkg", listOf("install", packageName))
            val data = result.getOrNull()
            val code = data?.get("code")?.asInt ?: -1
            if (code == 0) Result.success(true)
            else Result.failure(Exception(data?.get("stderr")?.asString ?: "Install failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removePackage(packageName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = execCommand("opkg", listOf("remove", packageName))
            val data = result.getOrNull()
            val code = data?.get("code")?.asInt ?: -1
            if (code == 0) Result.success(true)
            else Result.failure(Exception(data?.get("stderr")?.asString ?: "Remove failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePackageLists(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = execCommand("opkg", listOf("update"))
            val data = result.getOrNull()
            val code = data?.get("code")?.asInt ?: -1
            if (code == 0) Result.success(true)
            else Result.failure(Exception(data?.get("stderr")?.asString ?: "Update failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== SYSTEM ACTIONS ====================

    suspend fun rebootRouter(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            execCommand("reboot")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shutdownRouter(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            execCommand("poweroff")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
