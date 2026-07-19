package com.secops.mobile.data.model

import com.google.gson.annotations.SerializedName

data class OllamaChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("stream") val stream: Boolean = false
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class OllamaChatResponse(
    @SerializedName("message") val message: ChatMessage,
    @SerializedName("done") val done: Boolean
)
