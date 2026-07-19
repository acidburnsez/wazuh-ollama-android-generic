package com.secops.mobile.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class CredentialsManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        "secops_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveWazuhUrl(url: String) = sharedPreferences.edit().putString("wazuh_url", url).apply()
    fun getWazuhUrl(): String = sharedPreferences.getString("wazuh_url", "https://wazuh.example.com/wazuh-api/") ?: "https://wazuh.example.com/wazuh-api/"

    fun getGatewayUrl(): String {
        val url = getWazuhUrl()
        return if (url.contains("/wazuh-api/")) {
            url.substringBefore("/wazuh-api/") + "/"
        } else if (url.contains("/api/")) {
            url.substringBefore("/api/") + "/"
        } else {
            url
        }
    }
    
    fun saveWazuhToken(token: String?) = sharedPreferences.edit().putString("wazuh_token", token).apply()
    fun getWazuhToken(): String? = sharedPreferences.getString("wazuh_token", null)
    
    fun saveOllamaUrl(url: String) = sharedPreferences.edit().putString("ollama_url", url).apply()
    fun getOllamaUrl(): String = sharedPreferences.getString("ollama_url", "https://ollama.example.com/") ?: "https://ollama.example.com/"

    fun saveWazuhUsername(username: String) = sharedPreferences.edit().putString("wazuh_username", username).apply()
    fun getWazuhUsername(): String = sharedPreferences.getString("wazuh_username", "wazuh") ?: "wazuh"

    fun saveWazuhPassword(password: String) = sharedPreferences.edit().putString("wazuh_password", password).apply()
    fun getWazuhPassword(): String = sharedPreferences.getString("wazuh_password", "YOUR_WAZUH_PASSWORD") ?: "YOUR_WAZUH_PASSWORD"

    fun saveGotifyUrl(url: String) = sharedPreferences.edit().putString("gotify_url", url).apply()
    fun getGotifyUrl(): String = sharedPreferences.getString("gotify_url", "https://gotify.example.com/") ?: "https://gotify.example.com/"

    fun saveGotifyToken(token: String) = sharedPreferences.edit().putString("gotify_token", token).apply()
    fun getGotifyToken(): String = sharedPreferences.getString("gotify_token", "YOUR_GOTIFY_TOKEN") ?: "YOUR_GOTIFY_TOKEN"

    fun saveGiteaUrl(url: String) = sharedPreferences.edit().putString("gitea_url", url).apply()
    fun getGiteaUrl(): String = sharedPreferences.getString("gitea_url", "https://gitea.example.com/") ?: "https://gitea.example.com/"

    fun saveGiteaToken(token: String) = sharedPreferences.edit().putString("gitea_token", token).apply()
    fun getGiteaToken(): String = sharedPreferences.getString("gitea_token", "YOUR_GITEA_TOKEN") ?: "YOUR_GITEA_TOKEN"

    fun saveAutheliaSession(cookie: String?) {
        if (cookie == null) {
            sharedPreferences.edit().remove("authelia_session").apply()
        } else {
            sharedPreferences.edit().putString("authelia_session", cookie).apply()
        }
    }
    fun getAutheliaSession(): String? = sharedPreferences.getString("authelia_session", null)
}
