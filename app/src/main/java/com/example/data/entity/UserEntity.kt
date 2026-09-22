package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Role

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val passwordHash: String, // Demo stored password hash/plain
    val fullName: String,
    val role: Role,
    val email: String,
    val phone: String = "",
    val active: Boolean = true,
    val assignedClassIdsCsv: String = "", // Comma-separated class IDs assigned to teacher or ALL
    val canTakeAllAttendance: Boolean = true, // Universal Attendance: Any teacher can take attendance for any class
    val canManageStudents: Boolean = true,
    val canManageClasses: Boolean = true,
    val canViewReports: Boolean = true,
    val collaborationStatus: String = "APPROVED" // "APPROVED", "PENDING", "REJECTED"
) {
    fun getAssignedClassIds(): List<String> {
        if (assignedClassIdsCsv.isBlank() || assignedClassIdsCsv.equals("ALL", ignoreCase = true)) return emptyList()
        return assignedClassIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val isMasterAdmin: Boolean
        get() = email.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
                username.equals("jaitifoundation@gmail.com", ignoreCase = true) ||
                email.equals("admin@jaiti.in", ignoreCase = true) ||
                username.equals("admin", ignoreCase = true) ||
                role == Role.ADMIN

    val isJaitiApproved: Boolean
        get() = isMasterAdmin || collaborationStatus.equals("APPROVED", ignoreCase = true)

    val isPendingApproval: Boolean
        get() = !isMasterAdmin && (collaborationStatus.equals("PENDING", ignoreCase = true) || collaborationStatus.equals("RESTRICTED", ignoreCase = true))
}

