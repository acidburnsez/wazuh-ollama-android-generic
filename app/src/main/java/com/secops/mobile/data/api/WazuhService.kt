package com.secops.mobile.data.api

import com.secops.mobile.data.model.WazuhAuthResponse
import com.secops.mobile.data.model.WazuhAlertResponse
import com.secops.mobile.data.model.WazuhAgentsResponse
import com.secops.mobile.data.model.WazuhRestartAgentResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query

interface WazuhService {
    @GET("security/user/authenticate")
    suspend fun authenticate(
        @Header("Authorization") basicAuthHeader: String
    ): WazuhAuthResponse
    
    @GET("alerts")
    suspend fun getAlerts(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String = "-timestamp",
        @Query("level") minLevel: Int? = null
    ): WazuhAlertResponse

    @GET("agents")
    suspend fun getAgents(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 100
    ): WazuhAgentsResponse

    @PUT("agents/restart")
    suspend fun restartAgent(
        @Header("Authorization") bearerToken: String,
        @Query("agents_list") agentId: String
    ): WazuhRestartAgentResponse

    companion object {
        fun create(baseUrl: String, credentialsManager: com.secops.mobile.data.security.CredentialsManager): WazuhService {
            val unsafeBuilder = getUnsafeOkHttpClientBuilder()
            
            unsafeBuilder.addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                
                // Add Authelia session cookie if available to bypass the SSO gateway
                credentialsManager.getAutheliaSession()?.let { session ->
                    builder.addHeader("Cookie", session)
                }
                
                chain.proceed(builder.build())
            }
            
            val okHttpClient = unsafeBuilder
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val retrofit = retrofit2.Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()

            return retrofit.create(WazuhService::class.java)
        }
        
        private fun getUnsafeOkHttpClientBuilder(): okhttp3.OkHttpClient.Builder {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            return okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }
    }
}
