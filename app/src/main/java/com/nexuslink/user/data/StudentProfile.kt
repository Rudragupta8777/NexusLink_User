package com.nexuslink.user.data

data class StudentProfile(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val location: String? = null,
    val careerObjective: String? = null,
    val resumeUrl: String? = null,
    val education: ArrayList<Map<String, Any>> = arrayListOf(),
    val workExperience: ArrayList<Map<String, Any>> = arrayListOf(),
    val extracurriculars: ArrayList<Map<String, Any>> = arrayListOf(),
    val trainings: ArrayList<Map<String, Any>> = arrayListOf(),
    val projects: ArrayList<Map<String, Any>> = arrayListOf(),
    val skills: ArrayList<String> = arrayListOf(),
    val portfolio: Map<String, Any>? = null,
    val accomplishments: ArrayList<Map<String, Any>> = arrayListOf()
)