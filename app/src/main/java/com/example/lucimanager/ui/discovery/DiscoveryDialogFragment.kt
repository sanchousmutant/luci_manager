package com.example.lucimanager.ui.discovery

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lucimanager.R
import com.example.lucimanager.databinding.DialogDiscoveryBinding
import com.example.lucimanager.model.DiscoveredRouter
import com.example.lucimanager.service.RouterDiscoveryService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiscoveryDialogFragment : DialogFragment() {

    var onRouterSelected: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDiscoveryBinding.inflate(LayoutInflater.from(context))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.discovery_title)
            .setView(binding.root)
            .setNegativeButton(R.string.discovery_cancel, null)
            .create()

        val adapter = DiscoveryAdapter { ip ->
            onRouterSelected?.invoke(ip)
            dialog.dismiss()
        }
        binding.discoveryRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.discoveryRecycler.adapter = adapter

        CoroutineScope(Dispatchers.Main).launch {
            val service = RouterDiscoveryService(requireContext())
            val routers = service.discoverRouters()

            binding.discoveryProgress.visibility = View.GONE
            if (routers.isEmpty()) {
                binding.discoveryStatus.text = getString(R.string.discovery_not_found)
            } else {
                binding.discoveryStatus.text = getString(R.string.discovery_found)
                binding.discoveryRecycler.visibility = View.VISIBLE
                adapter.submitList(routers)
            }
        }

        return dialog
    }

    private class DiscoveryAdapter(
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<DiscoveryAdapter.ViewHolder>() {

        private var items = listOf<DiscoveredRouter>()

        fun submitList(list: List<DiscoveredRouter>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                android.R.layout.simple_list_item_2, parent, false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val router = items[position]
            holder.bind(router, onSelect)
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(router: DiscoveredRouter, onSelect: (String) -> Unit) {
                val ctx = itemView.context
                itemView.findViewById<TextView>(android.R.id.text1)?.text = router.ipAddress
                itemView.findViewById<TextView>(android.R.id.text2)?.text =
                    String.format(ctx.getString(R.string.discovery_response_time), router.responseTime)
                itemView.setOnClickListener { onSelect(router.ipAddress) }
            }
        }
    }
}
