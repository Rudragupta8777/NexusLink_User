package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Education(
    val degree: String = "",
    val institution: String = "",
    val startYear: Int = 0,
    val endYear: Int = 0
) : Parcelable
