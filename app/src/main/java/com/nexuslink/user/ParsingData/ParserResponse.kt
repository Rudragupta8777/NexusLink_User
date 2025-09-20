package com.nexuslink.user.ParsingData

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.nexuslink.user.data.Education
import com.nexuslink.user.data.Portfolio
import com.nexuslink.user.data.Project
import com.nexuslink.user.data.Training
import com.nexuslink.user.data.WorkExperience
import kotlinx.parcelize.Parcelize

@Parcelize
data class ParsedResumeData(
    val phone: String?,
    val location: String?,
    @SerializedName("career_objective")
    val careerObjective: String?,
    val portfolio: Portfolio?,
    val skills: List<String>?,
    val education: List<Education>?,
    @SerializedName("work_experience")
    val workExperience: List<WorkExperience>?,
    val projects: List<Project>?,
    val trainings: List<Training>?
) : Parcelable