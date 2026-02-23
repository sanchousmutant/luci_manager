package com.example.lucimanager.utils

object NetworkUtils {

    /**
     * Validate IP address format
     */
    fun isValidIPAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
}
