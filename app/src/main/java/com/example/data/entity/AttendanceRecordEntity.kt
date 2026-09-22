package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AttendanceStatus

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["studentId", "date"], unique = true)
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey val attendanceId: String,
    val studentId: String,
    val studentName: String,
    val classId: String,
    val className: String,
    val date: String, // Format: YYYY-MM-DD
    val status: AttendanceStatus, // PRESENT or ABSENT
    val remark: String = "",
    val teacherId: String,
    val teacherName: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
