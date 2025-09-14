package com.nexuslink.user.data

data class Student(
    val _id: String = "",
    val email: String = "",
    val name: String = "",
    val idcard_uid: String? = null,
    val gender: String? = null // Add this line if missing
)
