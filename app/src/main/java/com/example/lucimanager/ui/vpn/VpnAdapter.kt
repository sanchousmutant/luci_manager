package com.example.lucimanager.ui.vpn

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lucimanager.R
import com.example.lucimanager.databinding.ItemVpnBinding
import com.example.lucimanager.model.VpnConnection
import com.example.lucimanager.model.VpnType

class VpnAdapter(
    private val onToggleClick: (VpnConnection, Boolean) -> Unit
) : ListAdapter<VpnConnection, VpnAdapter.VpnViewHolder>(VpnDiffCallback()) {

    private var loadingVpn: String? = null

    fun setLoadingVpn(name: String?) {
        val old = loadingVpn
        loadingVpn = name
        old?.let { n -> currentList.indexOfFirst { it.name == n }.takeIf { it >= 0 }?.let { notifyItemChanged(it) } }
        name?.let { n -> currentList.indexOfFirst { it.name == n }.takeIf { it >= 0 }?.let { notifyItemChanged(it) } }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VpnViewHolder {
        val binding = ItemVpnBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VpnViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VpnViewHolder, position: Int) {
        holder.bind(getItem(position), loadingVpn == getItem(position).name, onToggleClick)
    }

    class VpnViewHolder(
        private val binding: ItemVpnBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(vpn: VpnConnection, isLoading: Boolean, onToggleClick: (VpnConnection, Boolean) -> Unit) {
            val ctx = binding.root.context
            binding.vpnName.text = vpn.name
            binding.vpnStatus.text = if (vpn.isActive) ctx.getString(R.string.vpn_active) else ctx.getString(R.string.vpn_inactive)
            binding.vpnType.text = when (vpn.type) {
                VpnType.OPENVPN -> ctx.getString(R.string.vpn_type_openvpn)
                VpnType.WIREGUARD -> ctx.getString(R.string.vpn_type_wireguard)
            }

            if (vpn.isActive) {
                binding.statusIndicator.setBackgroundColor(ctx.getColor(R.color.md_theme_primary))
            } else {
                binding.statusIndicator.setBackgroundColor(ctx.getColor(R.color.dark_gray))
            }

            if (isLoading) {
                binding.toggleButton.visibility = View.INVISIBLE
                binding.toggleProgress.visibility = View.VISIBLE
            } else {
                binding.toggleButton.visibility = View.VISIBLE
                binding.toggleProgress.visibility = View.GONE
                binding.toggleButton.text = if (vpn.isActive) ctx.getString(R.string.vpn_stop) else ctx.getString(R.string.vpn_start)
                binding.toggleButton.setOnClickListener {
                    onToggleClick(vpn, !vpn.isActive)
                }
            }
        }
    }
}

class VpnDiffCallback : DiffUtil.ItemCallback<VpnConnection>() {
    override fun areItemsTheSame(oldItem: VpnConnection, newItem: VpnConnection): Boolean = oldItem.name == newItem.name
    override fun areContentsTheSame(oldItem: VpnConnection, newItem: VpnConnection): Boolean = oldItem == newItem
}
