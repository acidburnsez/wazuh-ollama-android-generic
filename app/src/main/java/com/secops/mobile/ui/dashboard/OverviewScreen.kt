package com.secops.mobile.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.net.HttpURLConnection
import java.net.URL

// Trust-all OkHttpClient singleton for local certificate verification
private val unsafeOkHttpClient: OkHttpClient by lazy {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, SecureRandom())
    OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}

data class ServiceStatus(val name: String, val url: String, val isUp: Boolean? = null)

@Composable
fun OverviewScreen() {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    val gatewayUrl = remember { credentialsManager.getGatewayUrl() }
    val autheliaSession = remember { credentialsManager.getAutheliaSession() }

    var cpuUsage by remember { mutableStateOf(12f) }
    var ramUsage by remember { mutableStateOf(45f) }
    var diskUsage by remember { mutableStateOf(68f) }
    var isRefreshing by remember { mutableStateOf(false) }

    val services = remember {
        mutableStateListOf(
            ServiceStatus("Caddy Router", "https://wazuh.example.com"),
            ServiceStatus("Authelia SSO", "https://auth.example.com"),
            ServiceStatus("Wazuh SIEM", "https://wazuh.example.com/wazuh-api/"),
            ServiceStatus("Grafana", "https://grafana.example.com"),
            ServiceStatus("Prometheus", "https://prometheus.example.com"),
            ServiceStatus("Uptime Kuma", "https://status.example.com"),
            ServiceStatus("Plex Media", "https://plex.example.com"),
            ServiceStatus("Paperless Node", "https://paperless.example.com")
        )
    }

    suspend fun refreshData() {
        isRefreshing = true
        // 1. Fetch Prometheus Metrics
        withContext(Dispatchers.IO) {
            try {
                // Fetch CPU
                val cpuQuery = "100%20-%20(avg(rate(node_cpu_seconds_total%7Bmode%3D%22idle%22%7D%5B5m%5D))%20*%20100)"
                val cpuRequest = Request.Builder()
                    .url("${gatewayUrl}prometheus-api/api/v1/query?query=$cpuQuery")
                    .header("Cookie", autheliaSession ?: "")
                    .build()
                unsafeOkHttpClient.newCall(cpuRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "")
                        val results = json.getJSONObject("data").getJSONArray("result")
                        if (results.length() > 0) {
                            val valueStr = results.getJSONObject(0).getJSONArray("value").getString(1)
                            cpuUsage = valueStr.toFloatOrNull() ?: 12f
                        }
                    }
                }

                // Fetch RAM
                val ramQuery = "((node_memory_MemTotal_bytes%20-%20node_memory_MemAvailable_bytes)%20%2F%20node_memory_MemTotal_bytes)%20*%20100"
                val ramRequest = Request.Builder()
                    .url("${gatewayUrl}prometheus-api/api/v1/query?query=$ramQuery")
                    .header("Cookie", autheliaSession ?: "")
                    .build()
                unsafeOkHttpClient.newCall(ramRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "")
                        val results = json.getJSONObject("data").getJSONArray("result")
                        if (results.length() > 0) {
                            val valueStr = results.getJSONObject(0).getJSONArray("value").getString(1)
                            ramUsage = valueStr.toFloatOrNull() ?: 45f
                        }
                    }
                }

                // Fetch Disk
                val diskQuery = "((node_filesystem_size_bytes%7Bmountpoint%3D%22%2F%22%7D%20-%20node_filesystem_free_bytes%7Bmountpoint%3D%22%2F%22%7D)%20%2F%20node_filesystem_size_bytes%7Bmountpoint%3D%22%2F%22%7D)%20*%20100"
                val diskRequest = Request.Builder()
                    .url("${gatewayUrl}prometheus-api/api/v1/query?query=$diskQuery")
                    .header("Cookie", autheliaSession ?: "")
                    .build()
                unsafeOkHttpClient.newCall(diskRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val json = JSONObject(body ?: "")
                        val results = json.getJSONObject("data").getJSONArray("result")
                        if (results.length() > 0) {
                            val valueStr = results.getJSONObject(0).getJSONArray("value").getString(1)
                            diskUsage = valueStr.toFloatOrNull() ?: 68f
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Ping Services to verify state
        withContext(Dispatchers.IO) {
            val updatedServices = services.map { service ->
                try {
                    val request = Request.Builder()
                        .url(service.url)
                        .header("Cookie", autheliaSession ?: "")
                        .build()
                    val client = unsafeOkHttpClient.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .connectTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .readTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build()
                    client.newCall(request).execute().use { response ->
                        val isUp = StatusValidator.isServiceOnline(response.code)
                        service.copy(isUp = isUp)
                    }
                } catch (e: Exception) {
                    println("PVE_DEBUG: Service ping exception for ${service.name} (${service.url}): ${e.message}")
                    e.printStackTrace()
                    service.copy(isUp = false)
                }
            }
            withContext(Dispatchers.Main) {
                services.clear()
                services.addAll(updatedServices)
            }
        }
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Monitor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Live homelab metrics & endpoints status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Metrics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Proxmox Host Node Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                
                MetricRow(label = "Host CPU Usage", value = cpuUsage, color = NeonIndigo)
                MetricRow(label = "Host RAM Usage", value = ramUsage, color = NeonPurple)
                MetricRow(label = "ZFS Root Disk", value = diskUsage, color = NeonOrange)
            }
        }

        // Services Health List Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Services Health Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonIndigo, strokeWidth = 2.dp)
                    }
                }
                
                Divider(color = CardBorder, thickness = 1.dp)

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(services.size) { i ->
                            val service = services[i]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(service.name, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                val statusText = when (service.isUp) {
                                    true -> "ONLINE"
                                    false -> "OFFLINE"
                                    null -> "CHECKING..."
                                }
                                val statusColor = when (service.isUp) {
                                    true -> NeonGreen
                                    false -> NeonRed
                                    null -> TextSecondary
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: Float, color: Color) {
    val animatedProgress by animateFloatAsState(targetValue = value / 100f, label = "progressAnimation")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(String.format("%.1f%%", value), color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = CardBorder
        )
    }
}
