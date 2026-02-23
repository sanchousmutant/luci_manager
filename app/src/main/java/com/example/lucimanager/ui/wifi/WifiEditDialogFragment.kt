package com.example.lucimanager.ui.wifi

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.example.lucimanager.R
import com.example.lucimanager.databinding.DialogWifiEditBinding
import com.example.lucimanager.model.WifiNetwork
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WifiEditDialogFragment : DialogFragment() {

    var onSave: ((WifiNetwork) -> Unit)? = null
    private var network: WifiNetwork? = null

    companion object {
        private const val ARG_SSID = "ssid"
        private const val ARG_PASSWORD = "password"
        private const val ARG_CHANNEL = "channel"
        private const val ARG_TXPOWER = "txpower"
        private const val ARG_SECTION = "section"
        private const val ARG_RADIO = "radio"
        private const val ARG_IFNAME = "ifname"
        private const val ARG_MODE = "mode"
        private const val ARG_ENCRYPTION = "encryption"
        private const val ARG_FREQUENCY = "frequency"
        private const val ARG_ENABLED = "enabled"

        fun newInstance(network: WifiNetwork): WifiEditDialogFragment {
            return WifiEditDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SSID, network.ssid)
                    putString(ARG_PASSWORD, network.password)
                    putString(ARG_CHANNEL, network.channel)
                    putString(ARG_TXPOWER, network.txPower)
                    putString(ARG_SECTION, network.sectionName)
                    putString(ARG_RADIO, network.radioName)
                    putString(ARG_IFNAME, network.ifname)
                    putString(ARG_MODE, network.mode)
                    putString(ARG_ENCRYPTION, network.encryption)
                    putString(ARG_FREQUENCY, network.frequency)
                    putBoolean(ARG_ENABLED, network.enabled)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogWifiEditBinding.inflate(LayoutInflater.from(context))
        val args = requireArguments()

        binding.ssidEdit.setText(args.getString(ARG_SSID, ""))
        binding.passwordEdit.setText(args.getString(ARG_PASSWORD, ""))
        binding.channelEdit.setText(args.getString(ARG_CHANNEL, ""))
        binding.txpowerEdit.setText(args.getString(ARG_TXPOWER, ""))

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wifi_edit_title)
            .setView(binding.root)
            .setPositiveButton(R.string.wifi_save) { _, _ ->
                val updatedNetwork = WifiNetwork(
                    radioName = args.getString(ARG_RADIO, ""),
                    ifname = args.getString(ARG_IFNAME, ""),
                    ssid = binding.ssidEdit.text.toString(),
                    password = binding.passwordEdit.text.toString(),
                    channel = binding.channelEdit.text.toString(),
                    txPower = binding.txpowerEdit.text.toString(),
                    enabled = args.getBoolean(ARG_ENABLED, true),
                    mode = args.getString(ARG_MODE, "ap"),
                    encryption = args.getString(ARG_ENCRYPTION, ""),
                    frequency = args.getString(ARG_FREQUENCY, ""),
                    sectionName = args.getString(ARG_SECTION, "")
                )
                onSave?.invoke(updatedNetwork)
            }
            .setNegativeButton(R.string.wifi_cancel, null)
            .create()
    }
}
