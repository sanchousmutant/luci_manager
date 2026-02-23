package com.example.lucimanager.model

data class LuciSession(
    val token: String,          // ubus_rpc_session
    val ipAddress: String,
    val username: String,
    val isValid: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (5 * 60 * 1000) // 5 min default (ubus session)
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
    
    fun isValidSession(): Boolean {
        return isValid && !isExpired()
    }
    
}