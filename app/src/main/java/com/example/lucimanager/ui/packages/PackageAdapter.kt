package com.example.lucimanager.ui.packages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lucimanager.R
import com.example.lucimanager.databinding.ItemPackageBinding
import com.example.lucimanager.model.OpkgPackage

class PackageAdapter(
    private val onInstallClick: (String) -> Unit,
    private val onRemoveClick: (String) -> Unit
) : ListAdapter<OpkgPackage, PackageAdapter.PackageViewHolder>(PackageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val binding = ItemPackageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PackageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        holder.bind(getItem(position), onInstallClick, onRemoveClick)
    }

    class PackageViewHolder(
        private val binding: ItemPackageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pkg: OpkgPackage, onInstall: (String) -> Unit, onRemove: (String) -> Unit) {
            val ctx = binding.root.context
            binding.packageName.text = pkg.name
            binding.packageVersion.text = pkg.version

            if (pkg.description.isNotBlank()) {
                binding.packageDescription.text = pkg.description
                binding.packageDescription.visibility = View.VISIBLE
            } else {
                binding.packageDescription.visibility = View.GONE
            }

            if (pkg.isInstalled) {
                binding.actionButton.text = ctx.getString(R.string.packages_remove)
                binding.actionButton.setOnClickListener { onRemove(pkg.name) }
            } else {
                binding.actionButton.text = ctx.getString(R.string.packages_install)
                binding.actionButton.setOnClickListener { onInstall(pkg.name) }
            }
        }
    }
}

class PackageDiffCallback : DiffUtil.ItemCallback<OpkgPackage>() {
    override fun areItemsTheSame(oldItem: OpkgPackage, newItem: OpkgPackage): Boolean = oldItem.name == newItem.name
    override fun areContentsTheSame(oldItem: OpkgPackage, newItem: OpkgPackage): Boolean = oldItem == newItem
}
