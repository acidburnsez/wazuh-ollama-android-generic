package com.secops.mobile.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.launch

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

data class AlertManagerAlert(
    val alertname: String,
    val instance: String?,
    val job: String?,
    val severity: String,
    val summary: String,
    val description: String,
    val startsAt: String,
    val labels: Map<String, String>
)

@Composable
fun AlertManagerScreen() {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    val gatewayUrl = remember { credentialsManager.getGatewayUrl() }
    val autheliaSession = remember { credentialsManager.getAutheliaSession() }
    val coroutineScope = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<AlertManagerAlert>() }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val alertmanagerUrl = remember {
        val base = gatewayUrl.replace("wazuh.", "alertmanager.")
        if (base.endsWith("/")) base + "api/v2/alerts" else "$base/api/v2/alerts"
    }

    suspend fun fetchAlerts() {
        isRefreshing = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(alertmanagerUrl)
                    .header("Cookie", autheliaSession ?: "")
                    .build()
                unsafeOkHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val jsonArray = JSONArray(body)
                        val fetchedList = mutableListOf<AlertManagerAlert>()
                        for (i in 0 until jsonArray.length()) {
                            val alertObj = jsonArray.getJSONObject(i)
                            val labelsObj = alertObj.getJSONObject("labels")
                            val annotationsObj = alertObj.getJSONObject("annotations")
                            
                            val alertname = labelsObj.optString("alertname", "UnknownAlert")
                            val instance = labelsObj.optString("instance", null)
                            val job = labelsObj.optString("job", null)
                            val severity = labelsObj.optString("severity", "warning")
                            val summary = annotationsObj.optString("summary", "")
                            val description = annotationsObj.optString("description", "")
                            val startsAt = alertObj.optString("startsAt", "")
                            
                            val labelsMap = mutableMapOf<String, String>()
                            labelsObj.keys().forEach { key ->
                                labelsMap[key] = labelsObj.getString(key)
                            }
                            
                            fetchedList.add(
                                AlertManagerAlert(
                                    alertname = alertname,
                                    instance = instance,
                                    job = job,
                                    severity = severity,
                                    summary = summary,
                                    description = description,
                                    startsAt = startsAt,
                                    labels = labelsMap
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            alerts.clear()
                            alerts.addAll(fetchedList)
                        }
                    } else {
                        errorMessage = "Server returned error: ${response.code} ${response.message}"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Connection error: ${e.message}"
            }
        }
        isRefreshing = false
    }

    fun triggerPowerAction(vmid: Int, isLxc: Boolean, action: String) {
        coroutineScope.launch {
            actionMessage = "Sending $action to VMID $vmid..."
            val typePath = if (isLxc) "lxc" else "qemu"
            val url = "${gatewayUrl}pve-api/api2/json/nodes/prox/$typePath/$vmid/status/$action"
            withContext(Dispatchers.IO) {
                try {
                    val formBody = FormBody.Builder().build()
                    val request = Request.Builder()
                        .url(url)
                        .post(formBody)
                        .header("Cookie", autheliaSession ?: "")
                        .build()
                    unsafeOkHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            actionMessage = "Action $action scheduled successfully!"
                        } else {
                            actionMessage = "Failed: ${response.message}"
                        }
                    }
                } catch (e: Exception) {
                    actionMessage = "Error: ${e.message}"
                }
            }
        }
    }

    fun getMappedResource(alert: AlertManagerAlert): ProxmoxResource? {
        val instance = alert.instance ?: ""
        val labelsStr = alert.labels.toString().lowercase()
        return when {
            instance.contains("192.168.11.53") || labelsStr.contains("caddy") -> ProxmoxResource(303, "Caddy Reverse Proxy", true)
            instance.contains("192.168.11.69") || labelsStr.contains("gitea") -> ProxmoxResource(309, "Gitea Git Server", true)
            instance.contains("192.168.11.55") || labelsStr.contains("vault") -> ProxmoxResource(308, "HashiCorp Vault", true)
            instance.contains("192.168.11.50") || labelsStr.contains("wazuh") -> ProxmoxResource(310, "Wazuh SIEM Manager", true)
            instance.contains("192.168.11.54") || labelsStr.contains("gotify") -> ProxmoxResource(307, "Gotify Alerts Daemon", true)
            instance.contains("192.168.11.90") || labelsStr.contains("k3s-master-01") -> ProxmoxResource(700, "K3s Master 01", false)
            instance.contains("192.168.11.91") || labelsStr.contains("k3s-worker-01") -> ProxmoxResource(701, "K3s Worker 01 (GPU)", false)
            instance.contains("192.168.11.92") || labelsStr.contains("k3s-worker-02") -> ProxmoxResource(702, "K3s Worker 02", false)
            else -> null
        }
    }

    LaunchedEffect(Unit) {
        fetchAlerts()
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
                    text = "Alertmanager Incidents",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Active firing alerts in cluster & infrastructure",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            IconButton(
                onClick = { coroutineScope.launch { fetchAlerts() } }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Alerts", tint = TextPrimary)
            }
        }

        if (isRefreshing) {
            LinearProgressIndicator(
                color = NeonIndigo,
                trackColor = CardBackground,
                modifier = Modifier.fillMaxWidth()
            )
        }

        errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = err,
                    color = NeonRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        actionMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { actionMessage = null }) {
                        Text("Dismiss", color = TextSecondary)
                    }
                }
            }
        }

        if (alerts.isEmpty() && !isRefreshing && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No active firing incidents", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(alerts.size) { index ->
                    val alert = alerts[index]
                    val severityColor = when (alert.severity.lowercase()) {
                        "critical" -> NeonRed
                        "warning" -> NeonOrange
                        else -> NeonIndigo
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = alert.alertname,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Box(
                                    modifier = Modifier
                                        .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, severityColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = alert.severity.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = severityColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val detailsText = alert.description.ifEmpty { alert.summary }
                            if (detailsText.isNotEmpty()) {
                                Text(
                                    text = detailsText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (alert.instance != null) {
                                Text(
                                    text = "Instance: ${alert.instance}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            
                            val timeStr = try {
                                if (alert.startsAt.contains("T")) {
                                    alert.startsAt.substringAfter("T").substring(0, 8)
                                } else {
                                    alert.startsAt
                                }
                            } catch (e: Exception) {
                                alert.startsAt
                            }
                            Text(
                                text = "Triggered: $timeStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            // Remediation actions
                            val mappedRes = getMappedResource(alert)
                            if (mappedRes != null) {
                                Divider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = CardBorder
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Remediation target: ${mappedRes.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Row {
                                        Button(
                                            onClick = { triggerPowerAction(mappedRes.vmid, mappedRes.isLxc, "reboot") },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text("Reboot", color = Color.Black)
                                        }
                                        Button(
                                            onClick = { triggerPowerAction(mappedRes.vmid, mappedRes.isLxc, "start") },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Start", color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
