package com.example.lucimanager.model

data class ConnectedDevice(
    val mac: String = "",
    val ip: String = "",
    val hostname: String = "",
    val isWifi: Boolean = false,
    val signal: Int = 0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0
)
