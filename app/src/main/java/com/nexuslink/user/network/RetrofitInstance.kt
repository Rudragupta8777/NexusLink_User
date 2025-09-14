package com.nexuslink.user.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL = "https://604b31078aea.ngrok-free.app/"
    private const val AFFINDA_BASE_URL = "https://api.affinda.com/v3/" // Updated URL

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }

    val affindaApi: AffindaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AFFINDA_BASE_URL) // This is the correct base URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(AffindaApiService::class.java)
    }
}