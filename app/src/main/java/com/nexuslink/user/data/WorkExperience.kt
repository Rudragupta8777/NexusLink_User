package com.nexuslink.user.data

import java.util.Date

data class WorkExperience(
    var title: String = "",
    var company: String = "",
    var startDate: Date? = null,
    var endDate: Date? = null,
    var description: String = ""
)
