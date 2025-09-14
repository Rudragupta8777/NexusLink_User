package com.nexuslink.user.data

import java.util.Date

data class Training(
    var title: String = "",
    var issuer: String = "",
    var date: Date? = null,
    var description: String = ""
)

