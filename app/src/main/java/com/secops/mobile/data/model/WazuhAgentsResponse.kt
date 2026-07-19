package com.secops.mobile.data.model

import com.google.gson.annotations.SerializedName

data class WazuhAgentsResponse(
    @SerializedName("data") val data: AgentData,
    @SerializedName("error") val error: Int
)

data class AgentData(
    @SerializedName("affected_items") val affectedItems: List<WazuhAgentItem>,
    @SerializedName("total_affected_items") val totalAffectedItems: Int
)

data class WazuhAgentItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("ip") val ip: String?,
    @SerializedName("status") val status: String, // "active", "disconnected", "never_connected", "pending"
    @SerializedName("os") val os: OSInfo?
)

data class OSInfo(
    @SerializedName("name") val name: String?,
    @SerializedName("version") val version: String?
)

data class WazuhRestartAgentResponse(
    @SerializedName("data") val data: RestartData?,
    @SerializedName("error") val error: Int
)

data class RestartData(
    @SerializedName("affected_items") val affectedItems: List<String>?,
    @SerializedName("total_affected_items") val totalAffectedItems: Int
)
