package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Education(
    var degree: String = "",
    var institution: String = "",
    var startYear: Int = 0,
    var endYear: Int = 0
) : Parcelable