package com.example.lucimanager.model

data class RouterInfo(
    val hostname: String = "",
    val model: String = "",
    val firmwareVersion: String = "",
    val kernelVersion: String = "",
    val uptime: Long = 0,
    val memTotal: Long = 0,
    val memFree: Long = 0,
    val memBuffered: Long = 0,
    val loadAvg: List<Double> = listOf(0.0, 0.0, 0.0)
) {
    val memUsed: Long get() = memTotal - memFree - memBuffered
    val memUsagePercent: Int get() = if (memTotal > 0) ((memUsed * 100) / memTotal).toInt() else 0
}
