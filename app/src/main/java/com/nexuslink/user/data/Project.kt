package com.nexuslink.user.data

data class Project(
    var title: String = "",
    var duration: String = "",
    var link: String = "",
    var techStack: List<String> = emptyList(),
    var description: String = ""
)

