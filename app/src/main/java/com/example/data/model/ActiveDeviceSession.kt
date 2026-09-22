package com.example.data.model

data class ActiveDeviceSession(
    val sessionId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhone: String = "",
    val userRole: String = "TEACHER",
    val deviceModel: String = "",
    val deviceId: String = "",
    val loginTime: Long = System.currentTimeMillis(),
    val lastActiveTime: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE" // "ACTIVE", "DEACTIVATED", "DELETED"
) {
    val isActive: Boolean
        get() = status.equals("ACTIVE", ignoreCase = true)
}
