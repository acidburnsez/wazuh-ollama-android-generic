package com.secops.mobile.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secops.mobile.data.api.WazuhService
import com.secops.mobile.data.model.WazuhAgentItem
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen() {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    val scope = rememberCoroutineScope()
    
    var agentsList by remember { mutableStateOf<List<WazuhAgentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Status message for restart action
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val fetchAgents = {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val baseUrl = credentialsManager.getWazuhUrl()
                val user = credentialsManager.getWazuhUsername()
                val password = credentialsManager.getWazuhPassword()
                
                val basicAuth = okhttp3.Credentials.basic(user, password)
                val service = WazuhService.create(baseUrl, credentialsManager)
                
                val authResponse = withContext(Dispatchers.IO) {
                    service.authenticate(basicAuth)
                }
                val token = authResponse.data.token
                
                val agentsResponse = withContext(Dispatchers.IO) {
                    service.getAgents(bearerToken = "Bearer $token")
                }
                
                agentsList = agentsResponse.data.affectedItems
            } catch (e: Exception) {
                android.util.Log.e("AgentsScreen", "Error fetching agents", e)
                errorMsg = e.localizedMessage ?: "Connection error"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchAgents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SecOps Agents Control", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { fetchAgents() }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepObsidian,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepObsidian)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter by host name, status or IP...", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = NeonIndigo,
                    unfocusedBorderColor = CardBorder
                ),
                singleLine = true
            )

            if (actionMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NeonIndigo.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(actionMessage ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        TextButton(onClick = { actionMessage = null }) {
                            Text("Dismiss", color = NeonIndigo)
                        }
                    }
                }
            }

            if (isLoading && agentsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonIndigo)
                }
            } else if (errorMsg != null && agentsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Failed to retrieve agents", color = NeonRed, style = MaterialTheme.typography.bodyLarge)
                        Text(errorMsg ?: "", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { fetchAgents() }, colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)) {
                            Text("Retry", color = TextPrimary)
                        }
                    }
                }
            } else {
                val filteredAgents = agentsList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.ip ?: "").contains(searchQuery, ignoreCase = true) ||
                    it.status.contains(searchQuery, ignoreCase = true)
                }

                if (filteredAgents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching agents found.", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredAgents) { agent ->
                            val isActive = agent.status == "active"
                            val statusColor = if (isActive) NeonGreen else NeonRed
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = CardBackground)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(agent.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        
                                        // Status Dot indicator
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(statusColor, CircleShape)
                                            )
                                            Text(
                                                text = agent.status.uppercase(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = statusColor
                                            )
                                        }
                                    }
                                    
                                    Text("IP Address: ${agent.ip ?: "N/A"}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(
                                        text = "OS: ${agent.os?.name ?: "Unknown"} ${agent.os?.version ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Text("Agent ID: ${agent.id}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    
                                    if (isActive) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    actionMessage = "Sending restart command to agent ${agent.name}..."
                                                    try {
                                                        val baseUrl = credentialsManager.getWazuhUrl()
                                                        val user = credentialsManager.getWazuhUsername()
                                                        val password = credentialsManager.getWazuhPassword()
                                                        
                                                        val basicAuth = okhttp3.Credentials.basic(user, password)
                                                        val service = WazuhService.create(baseUrl, credentialsManager)
                                                        
                                                        val authResponse = withContext(Dispatchers.IO) {
                                                            service.authenticate(basicAuth)
                                                        }
                                                        val token = authResponse.data.token
                                                        
                                                        withContext(Dispatchers.IO) {
                                                            service.restartAgent(
                                                                bearerToken = "Bearer $token",
                                                                agentId = agent.id
                                                            )
                                                        }
                                                        actionMessage = "Restart request successfully sent to ${agent.name}!"
                                                    } catch (e: Exception) {
                                                        actionMessage = "Failed to restart agent: ${e.localizedMessage}"
                                                    }
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, NeonIndigo)
                                        ) {
                                            Text("Restart Agent Daemon", color = NeonIndigo, style = MaterialTheme.typography.labelMedium)
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
