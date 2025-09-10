package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WorkExperience(
    val title: String = "",
    val company: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String = ""
) : Parcelable
