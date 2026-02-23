package com.example.lucimanager.model

data class NetworkInterface(
    val name: String,
    val displayName: String,
    val isActive: Boolean,
    val protocol: String? = null,
    val ipAddress: String? = null,
    val device: String? = null
)