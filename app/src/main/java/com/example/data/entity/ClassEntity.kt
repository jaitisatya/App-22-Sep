package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val classId: String,
    val className: String,
    val roomOrLocation: String = "",
    val primaryTeacherId: String = "",
    val primaryTeacherName: String = "",
    val active: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis()
)
