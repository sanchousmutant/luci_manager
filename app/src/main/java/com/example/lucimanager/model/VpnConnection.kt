package com.example.lucimanager.model

data class VpnConnection(
    val name: String = "",
    val type: VpnType = VpnType.OPENVPN,
    val isActive: Boolean = false,
    val interfaceName: String = ""
)

enum class VpnType {
    OPENVPN, WIREGUARD
}
