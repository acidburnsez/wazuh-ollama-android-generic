package com.secops.mobile.data.api

import com.secops.mobile.data.model.OllamaChatRequest
import com.secops.mobile.data.model.OllamaChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface OllamaService {
    @POST("api/chat")
    suspend fun chat(
        @Body request: OllamaChatRequest
    ): OllamaChatResponse

    companion object {
        fun create(baseUrl: String): OllamaService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(OllamaService::class.java)
        }
    }
}
