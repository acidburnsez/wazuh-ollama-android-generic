package com.secops.mobile.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GiteaService {
    @POST("api/v1/repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Header("Authorization") tokenHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: GiteaCreateIssueRequest
    ): GiteaIssueResponse

    @GET("api/v1/repos/{owner}/{repo}/issues/{index}")
    suspend fun getIssue(
        @Header("Authorization") tokenHeader: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("index") index: Long
    ): GiteaIssueResponse

    companion object {
        fun create(baseUrl: String, credentialsManager: com.secops.mobile.data.security.CredentialsManager): GiteaService {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )

            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .addInterceptor { chain ->
                    val request = chain.request()
                    val builder = request.newBuilder()
                    
                    // Add Authelia session cookie if available
                    credentialsManager.getAutheliaSession()?.let { session ->
                        builder.addHeader("Cookie", session)
                    }
                    
                    chain.proceed(builder.build())
                }
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(GiteaService::class.java)
        }
    }
}

data class GiteaCreateIssueRequest(
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("labels") val labels: List<String> = emptyList()
)

data class GiteaIssueResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("number") val number: Long,
    @SerializedName("title") val title: String,
    @SerializedName("state") val state: String
)
