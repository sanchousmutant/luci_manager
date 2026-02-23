package com.example.lucimanager.ui.wifi

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lucimanager.R
import com.example.lucimanager.databinding.ItemWifiBinding
import com.example.lucimanager.model.WifiNetwork

class WifiAdapter(
    private val onEditClick: (WifiNetwork) -> Unit
) : ListAdapter<WifiNetwork, WifiAdapter.WifiViewHolder>(WifiDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val binding = ItemWifiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WifiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        holder.bind(getItem(position), onEditClick)
    }

    class WifiViewHolder(
        private val binding: ItemWifiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(network: WifiNetwork, onEditClick: (WifiNetwork) -> Unit) {
            val ctx = binding.root.context
            binding.wifiSsid.text = network.ssid.ifBlank { network.ifname }
            binding.wifiInfo.text = buildString {
                if (network.frequency.isNotBlank()) append(network.frequency)
                if (network.channel.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append("${ctx.getString(R.string.wifi_channel)} ${network.channel}")
                }
                if (network.encryption.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(network.encryption)
                }
            }
            binding.wifiStatus.text = if (network.enabled)
                ctx.getString(R.string.wifi_enabled)
            else
                ctx.getString(R.string.wifi_disabled)

            binding.editButton.setOnClickListener { onEditClick(network) }
        }
    }
}

class WifiDiffCallback : DiffUtil.ItemCallback<WifiNetwork>() {
    override fun areItemsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
        return oldItem.sectionName == newItem.sectionName
    }
    override fun areContentsTheSame(oldItem: WifiNetwork, newItem: WifiNetwork): Boolean {
        return oldItem == newItem
    }
}
