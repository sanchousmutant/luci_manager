package com.example.lucimanager.model

data class MonitoringState(
    val previousMacs: Set<String> = emptySet(),
    val isEnabled: Boolean = false,
    val intervalMinutes: Int = 15
)
