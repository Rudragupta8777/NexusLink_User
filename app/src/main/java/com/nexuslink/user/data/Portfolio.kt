package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Portfolio(
    val github: String?,
    val linkedin: String?,
    val otherLinks: List<String>?
) : Parcelable