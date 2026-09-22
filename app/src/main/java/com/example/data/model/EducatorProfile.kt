package com.example.data.model

data class EducatorProfile(
    val id: String,
    val name: String,
    val email: String = "",
    val photoUri: String = "",
    val phone: String = "",
    val subject: String = "",
    val createdTimestamp: Long = System.currentTimeMillis()
)
