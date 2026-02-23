package com.example.lucimanager.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lucimanager.network.LuciApiClient
import com.example.lucimanager.repository.NetworkRepository

class MonitoringWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = NetworkRepository(applicationContext)
        val monitoringState = repository.getMonitoringState()

        if (!monitoringState.isEnabled) {
            return Result.success()
        }

        try {
            // Login if needed
            val credentials = repository.getSavedCredentials() ?: return Result.failure()
            if (LuciApiClient.getCurrentSession() == null ||
                LuciApiClient.getCurrentSession()?.isExpired() == true) {
                val loginResult = LuciApiClient.login(credentials)
                if (loginResult.isFailure) return Result.retry()
            }

            // Get current devices
            val devicesResult = LuciApiClient.getConnectedDevices()
            val devices = devicesResult.getOrNull() ?: return Result.retry()

            val currentMacs = devices.map { it.mac }.toSet()
            val previousMacs = monitoringState.previousMacs

            // Find new devices
            if (previousMacs.isNotEmpty()) {
                val newMacs = currentMacs - previousMacs
                for (mac in newMacs) {
                    val device = devices.find { it.mac == mac }
                    val deviceInfo = device?.hostname?.ifBlank { device.ip.ifBlank { mac } } ?: mac
                    NotificationHelper.showNewDeviceNotification(applicationContext, deviceInfo)
                }
            }

            // Save current state
            repository.saveMonitoringState(
                monitoringState.copy(previousMacs = currentMacs)
            )

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "device_monitoring"
    }
}
