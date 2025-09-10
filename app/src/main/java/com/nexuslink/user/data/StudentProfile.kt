package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StudentProfile(
    var idcardUid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var gender: String = "",
    var location: String = "",
    var careerObjective: String = "",
    var resumeUrl: String = "",
    var education: MutableList<Education> = mutableListOf(),
    var workExperience: MutableList<WorkExperience> = mutableListOf(),
    var projects: MutableList<Project> = mutableListOf(),
    var skills: MutableList<String> = mutableListOf(),
    var portfolio: Portfolio = Portfolio() // Add this line
) : Parcelable