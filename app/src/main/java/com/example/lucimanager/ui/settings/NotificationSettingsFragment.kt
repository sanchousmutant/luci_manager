package com.example.lucimanager.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lucimanager.R
import com.example.lucimanager.databinding.FragmentNotificationSettingsBinding
import com.example.lucimanager.repository.NetworkRepository
import com.example.lucimanager.service.MonitoringWorker
import com.example.lucimanager.utils.showSuccessSnackbar
import java.util.concurrent.TimeUnit

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: NetworkRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = NetworkRepository(requireContext())
        val state = repository.getMonitoringState()

        binding.monitoringSwitch.isChecked = state.isEnabled
        when (state.intervalMinutes) {
            15 -> binding.interval15.isChecked = true
            30 -> binding.interval30.isChecked = true
            60 -> binding.interval60.isChecked = true
            else -> binding.interval15.isChecked = true
        }

        binding.monitoringSwitch.setOnCheckedChangeListener { _, isChecked ->
            val interval = getSelectedInterval()
            val newState = state.copy(isEnabled = isChecked, intervalMinutes = interval)
            repository.saveMonitoringState(newState)
            updateMonitoringWork(isChecked, interval)

            if (isChecked) {
                showSuccessSnackbar(getString(R.string.notification_monitoring_started))
            } else {
                showSuccessSnackbar(getString(R.string.notification_monitoring_stopped))
            }
        }

        binding.intervalGroup.setOnCheckedChangeListener { _, _ ->
            if (binding.monitoringSwitch.isChecked) {
                val interval = getSelectedInterval()
                val currentState = repository.getMonitoringState()
                repository.saveMonitoringState(currentState.copy(intervalMinutes = interval))
                updateMonitoringWork(true, interval)
            }
        }
    }

    private fun getSelectedInterval(): Int {
        return when (binding.intervalGroup.checkedRadioButtonId) {
            R.id.interval_15 -> 15
            R.id.interval_30 -> 30
            R.id.interval_60 -> 60
            else -> 15
        }
    }

    private fun updateMonitoringWork(enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(requireContext())

        if (enabled) {
            val workRequest = PeriodicWorkRequestBuilder<MonitoringWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                MonitoringWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(MonitoringWorker.WORK_NAME)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
