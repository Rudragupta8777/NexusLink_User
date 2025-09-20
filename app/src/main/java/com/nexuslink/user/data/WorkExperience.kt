package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class WorkExperience(
    var title: String = "",
    var company: String = "",
    var startDate: Long? = null, // Changed from Date? to Long? for Parcelize
    var endDate: Long? = null,   // Changed from Date? to Long? for Parcelize
    var description: String = ""
) : Parcelable