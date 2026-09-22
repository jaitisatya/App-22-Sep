package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "student_test_marks",
    indices = [
        Index(value = ["testId"]),
        Index(value = ["studentId"]),
        Index(value = ["classId"])
    ]
)
data class StudentTestMarksEntity(
    @PrimaryKey
    val markId: String, // e.g. "MARK_${testId}_${studentId}"
    val testId: String,
    val studentId: String,
    val studentName: String,
    val classId: String,
    val obtainedMarks: Double? = null, // null if absent or not yet graded
    val isAbsent: Boolean = false,
    val remark: String = "",
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
