package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val studentId: String,
    val studentName: String,
    val fatherName: String = "",
    val motherName: String = "",
    val classId: String = "",
    val phoneNumber: String = "",
    val photoUri: String = "",
    val age: Int = 0,
    val gender: String = "Male",
    val areaName: String = "",
    val schoolName: String = "",
    val active: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val dob: String = "",
    val notes: String = "",
    val schoolClass: String = "",
    val aadharCardUri: String = "",
    val birthCertificateUri: String = "",
    val consentFormUri: String = ""
)

