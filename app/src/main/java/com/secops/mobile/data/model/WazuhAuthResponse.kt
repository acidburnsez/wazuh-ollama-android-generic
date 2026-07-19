package com.secops.mobile.data.model

import com.google.gson.annotations.SerializedName

data class WazuhAuthResponse(
    @SerializedName("data") val data: AuthData,
    @SerializedName("error") val error: Int
)

data class AuthData(
    @SerializedName("token") val token: String
)
