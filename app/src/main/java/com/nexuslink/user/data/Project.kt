package com.nexuslink.user.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Project(
    val title: String = "",
    val duration: String = "",
    val link: String = "",
    val techStack: List<String> = emptyList(),
    val description: String = ""
) : Parcelable
