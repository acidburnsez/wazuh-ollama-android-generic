package com.secops.mobile.ui.dashboard

import android.webkit.CookieManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.data.api.WazuhService
import com.secops.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAlerts: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var alertsCount by remember { mutableStateOf("...") }
    var agentsCount by remember { mutableStateOf("...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun fetchLiveMetrics() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val wazuhUrl = credentialsManager.getWazuhUrl()
                val username = credentialsManager.getWazuhUsername()
                val password = credentialsManager.getWazuhPassword()
                
                val service = WazuhService.create(wazuhUrl, credentialsManager)
                
                var token = credentialsManager.getWazuhToken()
                if (token == null) {
                    val basicAuthHeader = "Basic " + android.util.Base64.encodeToString(
                        "$username:$password".toByteArray(),
                        android.util.Base64.NO_WRAP
                    )
                    val authResponse = withContext(Dispatchers.IO) {
                        service.authenticate(basicAuthHeader)
                    }
                    token = authResponse.data.token
                    credentialsManager.saveWazuhToken(token)
                }

                // 1. Fetch Alerts Count
                val alertsResponse = try {
                    withContext(Dispatchers.IO) {
                        service.getAlerts(bearerToken = "Bearer $token", limit = 1, minLevel = 5)
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401) {
                        credentialsManager.saveWazuhToken(null)
                        val basicAuthHeader = "Basic " + android.util.Base64.encodeToString(
                            "$username:$password".toByteArray(),
                            android.util.Base64.NO_WRAP
                        )
                        val authResponse = withContext(Dispatchers.IO) {
                            service.authenticate(basicAuthHeader)
                        }
                        token = authResponse.data.token
                        credentialsManager.saveWazuhToken(token)
                        
                        withContext(Dispatchers.IO) {
                            service.getAlerts(bearerToken = "Bearer $token", limit = 1, minLevel = 5)
                        }
                    } else {
                        throw e
                    }
                }
                alertsCount = alertsResponse.data.totalAffectedItems.toString()

                // 2. Fetch Agents List to count Active ones
                val agentsResponse = withContext(Dispatchers.IO) {
                    service.getAgents(bearerToken = "Bearer $token", limit = 100)
                }
                val activeCount = agentsResponse.data.affectedItems.count { it.status == "active" }
                agentsCount = activeCount.toString()

            } catch (e: Exception) {
                android.util.Log.e("WazuhDashboard", "Error fetching dashboard metrics", e)
                errorMessage = "API Error: ${e.localizedMessage ?: "Connection failed"}"
                alertsCount = "ERR"
                agentsCount = "ERR"
                if (e is retrofit2.HttpException && e.code() == 401) {
                    credentialsManager.saveWazuhToken(null)
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchLiveMetrics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header Section
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Security Operations",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Homelab Wazuh & Ollama Live Monitoring",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }

        // Error Message Display
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, NeonRed, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = NeonRed)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonRed
                    )
                }
            }
        }

        // Stats Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Alerts Card (Vibrant Neon Red Outline)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { onNavigateToAlerts() },
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Alerts", tint = NeonRed, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Active Alerts", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Text(alertsCount, style = MaterialTheme.typography.headlineLarge, color = NeonRed)
                }
            }

            // Active Agents Card (Vibrant Neon Green Outline)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { onNavigateToAgents() },
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Agents", tint = NeonGreen, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Active Agents", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Text(agentsCount, style = MaterialTheme.typography.headlineLarge, color = NeonGreen)
                }
            }
        }

        // Custom Sparkline Bezier Graph Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Alert Volume (Last 24 Hours)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Trend analysis from all nodes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(20.dp))
                
                // Bezier Line Chart
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val points = listOf(
                        Offset(0f, 100f),
                        Offset(80f, 110f),
                        Offset(160f, 40f),
                        Offset(240f, 90f),
                        Offset(320f, 20f),
                        Offset(400f, 70f),
                        Offset(480f, 10f),
                        Offset(560f, 115f),
                        Offset(640f, 60f),
                        Offset(720f, 5f)
                    )
                    
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val p1 = points[i - 1]
                            val p2 = points[i]
                            cubicTo(
                                (p1.x + p2.x) / 2, p1.y,
                                (p1.x + p2.x) / 2, p2.y,
                                p2.x, p2.y
                            )
                        }
                    }
                    
                    // Draw Path Stroke
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(listOf(NeonIndigo, NeonPurple)),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Navigation Button with Gradient
        Button(
            onClick = onNavigateToAlerts,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(14.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
        ) {
            Icon(Icons.Default.List, contentDescription = "Alerts Feed", tint = TextPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Live Alerts Feed", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
    }
}
