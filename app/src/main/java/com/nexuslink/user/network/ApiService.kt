package com.nexuslink.user.network

import com.nexuslink.user.data.AttachIdRequest
import com.nexuslink.user.data.GenderRequest
import com.nexuslink.user.data.LoginResponse
import com.nexuslink.user.data.StudentProfile
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Header


interface ApiService {

    @POST("user/login")
    fun login(@Header("Authorization") token: String): Call<LoginResponse>

    @PATCH("user/attach-id")
    fun attachId(
        @Header("Authorization") token: String,
        @Body body: AttachIdRequest
    ): Call<LoginResponse>

    @PATCH("user/gender")
    fun updateGender(@Header("Authorization") token: String, @Body body: GenderRequest): Call<LoginResponse>

    @PATCH("user/profile")
    fun updateProfile(@Header("Authorization") token: String, @Body profile: StudentProfile): Call<StudentProfile>

    // Add this missing method
    @GET("user/profile")
    fun getProfile(@Header("Authorization") token: String): Call<StudentProfile>
}