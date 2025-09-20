package com.nexuslink.user.network

import com.nexuslink.user.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ParserRetrofitInstance {
    private const val API_KEY = BuildConfig.GEMINI_API_KEY
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val api: ParserApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ParserApiService::class.java)
    }

    // A helper function to get the full endpoint URL with the key
    fun getUrlWithKey(endpoint: String): String {
        return "$endpoint?key=$API_KEY"
    }
}