package com.secops.mobile.data.model

import com.google.gson.annotations.SerializedName

data class WazuhAlertResponse(
    @SerializedName("data") val data: AlertData,
    @SerializedName("error") val error: Int
)

data class AlertData(
    @SerializedName("affected_items") val affectedItems: List<WazuhAlertItem>,
    @SerializedName("total_affected_items") val totalAffectedItems: Int
)

data class WazuhAlertItem(
    @SerializedName("id") val id: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("rule") val rule: RuleInfo,
    @SerializedName("agent") val agent: AgentInfo,
    @SerializedName("full_log") val fullLog: String?
)

data class RuleInfo(
    @SerializedName("id") val id: String,
    @SerializedName("level") val level: Int,
    @SerializedName("description") val description: String
)

data class AgentInfo(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("ip") val ip: String?
)
