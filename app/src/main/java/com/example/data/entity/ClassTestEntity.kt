package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "class_tests",
    indices = [
        Index(value = ["classId"]),
        Index(value = ["testDate"]),
        Index(value = ["subject"])
    ]
)
data class ClassTestEntity(
    @PrimaryKey
    val testId: String, // e.g. "TEST_CLASSID_TIMESTAMP"
    val classId: String,
    val className: String,
    val testTitle: String, // e.g. "Weekly Test 1", "Unit Assessment"
    val subject: String, // e.g. "Mathematics", "Hindi"
    val testDate: String, // ISO "yyyy-MM-dd"
    val totalMarks: Double, // e.g. 20.0, 50.0, 100.0
    val passingMarks: Double = 0.0,
    val teacherId: String = "",
    val teacherName: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
