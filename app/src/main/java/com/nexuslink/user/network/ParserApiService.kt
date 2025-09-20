package com.nexuslink.user.network

import com.nexuslink.user.ParsingData.GeminiRequest
import com.nexuslink.user.ParsingData.GeminiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface ParserApiService {
    @POST
    fun getParsedResume(
        @Url url: String,
        @Body request: GeminiRequest
    ): Call<GeminiResponse>
}