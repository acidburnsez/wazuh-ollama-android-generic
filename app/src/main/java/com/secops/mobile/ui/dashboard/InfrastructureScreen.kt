package com.secops.mobile.ui.dashboard

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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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

data class ProxmoxResource(val vmid: Int, val name: String, val isLxc: Boolean, val status: String = "unknown")
data class K3sService(val name: String, val url: String, val status: String = "unknown")

@Composable
fun InfrastructureScreen() {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    val gatewayUrl = remember { credentialsManager.getGatewayUrl() }
    val autheliaSession = remember { credentialsManager.getAutheliaSession() }
    val coroutineScope = rememberCoroutineScope()

    var selectedSubTab by remember { mutableStateOf(0) }
    val subTabs = listOf("Proxmox Nodes", "K3s Services")

    val resources = remember {
        mutableStateListOf(
            ProxmoxResource(600, "OPNsense Gateway", false),
            ProxmoxResource(303, "Caddy Reverse Proxy", true),
            ProxmoxResource(309, "Gitea Git Server", true),
            ProxmoxResource(308, "HashiCorp Vault", true),
            ProxmoxResource(310, "Wazuh SIEM Manager", true),
            ProxmoxResource(307, "Gotify Alerts Daemon", true),
            ProxmoxResource(700, "K3s Master 01", false),
            ProxmoxResource(701, "K3s Worker 01 (GPU)", false),
            ProxmoxResource(702, "K3s Worker 02", false)
        )
    }

    val k3sServices = remember {
        mutableStateListOf(
            K3sService("Authelia SSO", "https://auth.example.com"),
            K3sService("Homepage Dashboard", "https://homepage.example.com"),
            K3sService("Uptime Kuma Status", "https://status.example.com"),
            K3sService("Ollama Local LLM", "https://ollama.example.com"),
            K3sService("Plex Server", "https://plex.example.com"),
            K3sService("Paperless-ngx", "https://paperless.example.com"),
            K3sService("Grafana Dashboards", "https://grafana.example.com"),
            K3sService("Prometheus UI", "https://prometheus.example.com"),
            K3sService("NeoBear Web IRC", "https://chat.example.com"),
            K3sService("NeoDrop File Sharing", "https://drops.example.com")
        )
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refreshStatuses() {
        isRefreshing = true
        withContext(Dispatchers.IO) {
            val updatedResources = resources.map { res ->
                val typePath = if (res.isLxc) "lxc" else "qemu"
                val url = "${gatewayUrl}pve-api/api2/json/nodes/prox/$typePath/${res.vmid}/status/current"
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Cookie", autheliaSession ?: "")
                        .build()
                    unsafeOkHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            val json = JSONObject(body ?: "")
                            val status = json.getJSONObject("data").getString("status")
                            res.copy(status = status)
                        } else {
                            res.copy(status = "error")
                        }
                    }
                } catch (e: Exception) {
                    res.copy(status = "offline")
                }
            }
            withContext(Dispatchers.Main) {
                resources.clear()
                resources.addAll(updatedResources)
            }
        }
        isRefreshing = false
    }

    suspend fun refreshK3sStatuses() {
        isRefreshing = true
        withContext(Dispatchers.IO) {
            val updatedK3s = k3sServices.map { service ->
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
                        service.copy(status = if (isUp) "running" else "stopped")
                    }
                } catch (e: Exception) {
                    service.copy(status = "offline")
                }
            }
            withContext(Dispatchers.Main) {
                k3sServices.clear()
                k3sServices.addAll(updatedK3s)
            }
        }
        isRefreshing = false
    }

    suspend fun performRefresh() {
        if (selectedSubTab == 0) {
            refreshStatuses()
        } else {
            refreshK3sStatuses()
        }
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
            refreshStatuses()
        }
    }

    LaunchedEffect(selectedSubTab) {
        performRefresh()
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
                    text = "Homelab Controller",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Manage host nodes and monitor cluster services",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonIndigo, strokeWidth = 2.dp)
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = CardBackground,
            contentColor = NeonIndigo,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(title, color = if (selectedSubTab == index) NeonIndigo else TextSecondary, style = MaterialTheme.typography.titleSmall) }
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

        if (selectedSubTab == 0) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(resources.size) { i ->
                    val res = resources[i]
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
                                Column {
                                    Text(res.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("VMID: ${res.vmid} | ${if (res.isLxc) "LXC Container" else "QEMU VM"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                
                                val statusColor = when (res.status) {
                                    "running" -> NeonGreen
                                    "stopped" -> NeonRed
                                    "offline", "error" -> TextSecondary
                                    else -> NeonOrange
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(res.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = statusColor)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (res.status == "stopped") {
                                    Button(
                                        onClick = { triggerPowerAction(res.vmid, res.isLxc, "start") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text("Start", color = Color.Black)
                                    }
                                } else if (res.status == "running") {
                                    Button(
                                        onClick = { triggerPowerAction(res.vmid, res.isLxc, "reboot") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text("Reboot", color = Color.Black)
                                    }
                                    Button(
                                        onClick = { triggerPowerAction(res.vmid, res.isLxc, "stop") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Stop", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(k3sServices.size) { i ->
                    val service = k3sServices[i]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(service.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(service.url, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            
                            val statusColor = when (service.status) {
                                "running" -> NeonGreen
                                "stopped" -> NeonRed
                                "offline" -> TextSecondary
                                else -> NeonOrange
                            }
                            val statusText = when (service.status) {
                                "running" -> "ONLINE"
                                "stopped" -> "DOWN"
                                "offline" -> "OFFLINE"
                                else -> "CHECKING"
                            }
                            Box(
                                modifier = Modifier
                                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
