package com.example.lucimanager.model

data class LuciSession(
    val token: String,
    val sessionId: String,
    val ipAddress: String,
    val username: String,
    val isValid: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30 * 60 * 1000) // 30 minutes default expiration
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
    
    fun isValidSession(): Boolean {
        return isValid && !isExpired()
    }
    
    fun isAboutToExpire(): Boolean {
        val timeToExpiration = expiresAt - System.currentTimeMillis()
        return timeToExpiration < (5 * 60 * 1000) // 5 minutes before expiration
    }
}