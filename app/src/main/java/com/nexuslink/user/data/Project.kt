package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Project(
    var title: String = "",
    var description: String = "",
    var link: String? = null,
    var techStack: List<String> = emptyList(),
    var duration: String? = null
) : Parcelable

