package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Training(
    var title: String = "",
    var issuer: String = "",
    var description: String? = null,
    var date: Long? = null // Changed from Date? to Long? for Parcelize
) : Parcelable