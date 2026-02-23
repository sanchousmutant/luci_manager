package com.example.lucimanager.model

data class WifiNetwork(
    val radioName: String = "",
    val ifname: String = "",
    val ssid: String = "",
    val password: String = "",
    val channel: String = "auto",
    val txPower: String = "",
    val enabled: Boolean = true,
    val mode: String = "ap",
    val encryption: String = "",
    val frequency: String = "",
    val sectionName: String = ""
)
