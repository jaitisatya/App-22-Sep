package com.example.data

import androidx.room.TypeConverter
import com.example.data.model.AttendanceStatus
import com.example.data.model.Role

class Converters {
    @TypeConverter
    fun fromRole(role: Role?): String {
        return role?.name ?: Role.TEACHER.name
    }

    @TypeConverter
    fun toRole(value: String?): Role {
        return try {
            if (value.isNullOrBlank()) Role.TEACHER else Role.valueOf(value)
        } catch (_: Exception) {
            Role.TEACHER
        }
    }

    @TypeConverter
    fun fromAttendanceStatus(status: AttendanceStatus?): String {
        return status?.name ?: AttendanceStatus.PRESENT.name
    }

    @TypeConverter
    fun toAttendanceStatus(value: String?): AttendanceStatus {
        return try {
            if (value.isNullOrBlank()) AttendanceStatus.PRESENT else AttendanceStatus.valueOf(value)
        } catch (_: Exception) {
            AttendanceStatus.PRESENT
        }
    }
}
