package com.example.lucimanager.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lucimanager.R
import com.example.lucimanager.databinding.ItemDeviceBinding
import com.example.lucimanager.model.ConnectedDevice

class DeviceAdapter : ListAdapter<ConnectedDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: ConnectedDevice) {
            val ctx = binding.root.context
            binding.deviceHostname.text = device.hostname.ifBlank { ctx.getString(R.string.devices_unknown_host) }
            binding.deviceIpMac.text = "${device.ip} • ${device.mac}"

            val connType = if (device.isWifi) {
                val signalStr = if (device.signal != 0) " • ${ctx.getString(R.string.devices_signal)}: ${device.signal} dBm" else ""
                "${ctx.getString(R.string.devices_wifi_client)}$signalStr"
            } else {
                ctx.getString(R.string.devices_wired_client)
            }
            binding.deviceConnectionType.text = connType

            val icon = if (device.isWifi) R.drawable.ic_wifi else R.drawable.ic_devices
            binding.deviceIcon.setImageResource(icon)
        }
    }
}

class DeviceDiffCallback : DiffUtil.ItemCallback<ConnectedDevice>() {
    override fun areItemsTheSame(oldItem: ConnectedDevice, newItem: ConnectedDevice): Boolean = oldItem.mac == newItem.mac
    override fun areContentsTheSame(oldItem: ConnectedDevice, newItem: ConnectedDevice): Boolean = oldItem == newItem
}
