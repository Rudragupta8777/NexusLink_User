package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class ParsedResumeData(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val careerObjective: String? = null,
    val education: ArrayList<@RawValue Map<String, Any>> = arrayListOf(),
    val workExperience: ArrayList<@RawValue Map<String, Any>> = arrayListOf(),
    val projects: ArrayList<@RawValue Map<String, Any>> = arrayListOf(),
    val skills: ArrayList<String> = arrayListOf(),
    val accomplishments: ArrayList<@RawValue Map<String, Any>> = arrayListOf(),
    val trainings: ArrayList<@RawValue Map<String, Any>> = arrayListOf(),
    val extracurriculars: ArrayList<@RawValue Map<String, Any>> = arrayListOf(),
    val portfolio: @RawValue Map<String, String> = mapOf()
) : Parcelable