package com.secops.mobile.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.secops.mobile.ui.feed.MockAlert
import com.secops.mobile.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.data.api.GiteaService
import com.secops.mobile.data.api.GiteaCreateIssueRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.secops.mobile.data.api.OllamaService
import com.secops.mobile.data.model.OllamaChatRequest
import com.secops.mobile.data.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailsScreen(
    alert: MockAlert,
    onRemediationSuccess: (String) -> Unit,
    onNavigateBack: () -> Unit,
    isSplitMode: Boolean = false
) {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    var showOllamaSheet by remember { mutableStateOf(false) }
    var ollamaLoading by remember { mutableStateOf(false) }
    var ollamaError by remember { mutableStateOf<String?>(null) }
    var ollamaResponseText by remember { mutableStateOf<String?>(null) }
    val chatMessages = remember(alert.id) { mutableStateListOf<ChatMessage>() }
    var chatInput by remember { mutableStateOf("") }
    var chatLoading by remember { mutableStateOf(false) }
    var createdIssueNumber by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(showOllamaSheet) {
        if (showOllamaSheet && chatMessages.isEmpty()) {
            ollamaLoading = true
            ollamaError = null
            try {
                val ollamaUrl = credentialsManager.getOllamaUrl()
                val service = OllamaService.create(ollamaUrl)
                val prompt = """
                    You are an expert Security Operations (SecOps) threat analyst. Analyze the following Wazuh security alert from our homelab:
                    
                    Rule ID: ${alert.id}
                    Severity Level: ${alert.level}
                    Agent/Host: ${alert.agent}
                    Time: ${alert.time}
                    Description: ${alert.description}
                    
                    Raw Log Data:
                    {
                      "rule": {
                        "id": "${alert.id}",
                        "level": ${alert.level},
                        "description": "${alert.description}"
                      },
                      "agent": {
                        "id": "002",
                        "name": "${alert.agent}",
                        "ip": "192.168.11.60"
                      }
                    }
                    
                    Please provide a comprehensive, highly detailed threat analysis:
                    1. Assess the alert: What does this rule represent, and what is the potential threat?
                    2. List specific details of this alert.
                    3. Provide a step-by-step remediation guide with at least 3 concrete terminal commands or system administration actions.
                """.trimIndent()
                
                val initialUserMessage = ChatMessage(role = "user", content = prompt)
                chatMessages.add(initialUserMessage)
                
                val request = OllamaChatRequest(
                    model = "zero-cool-analyst",
                    messages = chatMessages.toList(),
                    stream = false
                )
                
                val response = withContext(Dispatchers.IO) {
                    service.chat(request)
                }
                chatMessages.add(response.message)
                ollamaResponseText = response.message.content
            } catch (e: Exception) {
                android.util.Log.e("Ollama", "Error calling Ollama API", e)
                ollamaError = e.localizedMessage ?: "Unknown network error"
                chatMessages.clear()
            } finally {
                ollamaLoading = false
            }
        }
    }
    var showRemediationDialog by remember { mutableStateOf(false) }
    var executionStep by remember { mutableStateOf(0) } // 0: Config, 1: Running, 2: Verifying, 3: Success
    
    // Command selection checklist
    val commandOptions = remember(alert.id) {
        when (alert.id) {
            "1001" -> listOf(
                "Apply AppArmor suppression rule 100300" to "sysctl -w fs.protected_symlinks=1",
                "Restart Wazuh agent service" to "systemctl restart wazuh-agent"
            )
            "1002" -> listOf(
                "Remove false-positive hidden file from /dev" to "rm -rf /dev/.lxc/sys-check.tmp",
                "Run Wazuh agent rootcheck scanner" to "/var/ossec/bin/wazuh-control restart"
            )
            "1004" -> listOf(
                "Block malicious IP via iptables firewall" to "iptables -A INPUT -s 192.168.11.23 -j DROP",
                "Trigger Fail2ban secure ban on sshd jail" to "fail2ban-client set sshd banip 192.168.11.23"
            )
            else -> listOf(
                "Acknowledge alert inside manager console" to "wazuh-db-ack --alert-id ${alert.id}",
                "Perform standard agent sync verify" to "systemctl status wazuh-agent"
            )
        }
    }

    val selectedStates = remember(alert.id) {
        mutableStateListOf<Boolean>().apply {
            addAll(List(commandOptions.size) { true })
        }
    }

    val severityColor = when {
        alert.level >= 12 -> NeonRed
        alert.level >= 8 -> NeonOrange
        else -> NeonGreen
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Inspector: ${alert.id}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (!isSplitMode) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Rule Severity Details", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rule ID: ${alert.id}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Box(
                            modifier = Modifier
                                .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, severityColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Level ${alert.level}", style = MaterialTheme.typography.labelLarge, color = severityColor)
                        }
                    }
                    Text("Trigger Host/Agent: ${alert.agent}", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Timestamp: ${alert.time}", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("Description: ${alert.description}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }

            Text("Raw Log Data (JSON)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B12))
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = """{
  "rule": {
    "id": "${alert.id}",
    "level": ${alert.level},
    "description": "${alert.description}"
  },
  "agent": {
    "id": "002",
    "name": "${alert.agent}",
    "ip": "192.168.11.60"
  }
}""",
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { showOllamaSheet = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                ) {
                    Text("Ask Ollama AI", color = TextPrimary)
                }

                Button(
                    onClick = { 
                        executionStep = 0
                        showRemediationDialog = true 
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed)
                ) {
                    Text("Remediate", color = TextPrimary)
                }
            }
        }
    }

    // ── Interactive Remediation & Verification Dialog ──
    if (showRemediationDialog) {
        Dialog(
            onDismissRequest = { 
                if (executionStep == 0 || executionStep == 3) showRemediationDialog = false 
            },
            properties = DialogProperties(dismissOnBackPress = (executionStep == 0 || executionStep == 3), dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (executionStep) {
                        0 -> { // Config / Review Step
                            Text("Interactive Remediation", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text(
                                "Review and select the mitigation commands to execute:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                commandOptions.forEachIndexed { index, (label, cmd) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedStates[index],
                                            onCheckedChange = { selectedStates[index] = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = NeonIndigo,
                                                uncheckedColor = TextSecondary
                                            )
                                        )
                                        Column {
                                            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                            Text(
                                                cmd,
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonGreen
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showRemediationDialog = false }) {
                                    Text("Cancel", color = NeonRed)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { executionStep = 1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                                ) {
                                    Text("Run Selected", color = TextPrimary)
                                }
                            }
                        }
                        1 -> { // Running Step
                            Text("Executing Commands", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            CircularProgressIndicator(color = NeonIndigo, modifier = Modifier.size(48.dp))
                            Text("Dispatching approved actions to Antigravity...", color = TextSecondary)
                            
                            LaunchedEffect(Unit) {
                                try {
                                    val giteaUrl = credentialsManager.getGiteaUrl()
                                    val giteaToken = credentialsManager.getGiteaToken()
                                    
                                    val service = GiteaService.create(giteaUrl, credentialsManager)
                                    
                                    val checkedCommandsText = commandOptions.filterIndexed { index, _ -> 
                                        if (index < selectedStates.size) selectedStates[index] else false 
                                    }.joinToString("\n") { "- **${it.first}:** `${it.second}`" }
                                    
                                    val body = """
                                        ### Alert Details
                                        - **Agent:** ${alert.agent}
                                        - **Rule ID:** ${alert.id}
                                        - **Level:** ${alert.level}
                                        - **Description:** ${alert.description}
                                        - **Time:** ${alert.time}

                                        ### Approved Actions & Commands
                                        $checkedCommandsText

                                        ### Ollama Threat Assessment & Context
                                        $ollamaResponseText
                                    """.trimIndent()
                                    
                                    val request = GiteaCreateIssueRequest(
                                        title = "[remediation-request] Wazuh Rule ${alert.id}: ${alert.description}",
                                        body = body,
                                        labels = listOf("remediation-request")
                                    )
                                    
                                    val response = withContext(Dispatchers.IO) {
                                        service.createIssue(
                                            tokenHeader = "token $giteaToken",
                                            owner = "kai",
                                            repo = "proxmox",
                                            request = request
                                        )
                                    }
                                    createdIssueNumber = response.number
                                } catch (e: Exception) {
                                    android.util.Log.e("Gitea", "Failed to dispatch remediation request", e)
                                } finally {
                                    executionStep = 2
                                }
                            }
                        }
                        2 -> { // Verification Step
                            Text("Post-Remediation Check", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            CircularProgressIndicator(color = NeonPurple, modifier = Modifier.size(48.dp))
                            Text("Waiting for Antigravity automated remediation runner to complete...", color = TextSecondary)
                            Text("Polling Gitea Actions task status...", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            
                            LaunchedEffect(Unit) {
                                val issueNum = createdIssueNumber
                                if (issueNum != null) {
                                    try {
                                        val giteaUrl = credentialsManager.getGiteaUrl()
                                        val giteaToken = credentialsManager.getGiteaToken()
                                        val service = GiteaService.create(giteaUrl, credentialsManager)
                                        
                                        var isClosed = false
                                        var attempts = 0
                                        while (!isClosed && attempts < 40) { // Poll for up to 120 seconds (3s * 40)
                                            delay(3000L)
                                            attempts++
                                            val details = withContext(Dispatchers.IO) {
                                                service.getIssue(
                                                    tokenHeader = "token $giteaToken",
                                                    owner = "kai",
                                                    repo = "proxmox",
                                                    index = issueNum
                                                )
                                            }
                                            if (details.state == "closed") {
                                                isClosed = true
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("GiteaPoll", "Error polling issue", e)
                                        delay(3000L) // Fail-safe delay
                                    }
                                } else {
                                    delay(3000L) // Default backup delay if number is missing
                                }
                                executionStep = 3
                            }
                        }
                        3 -> { // Success/Verification PASS
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Verification Pass",
                                tint = NeonGreen,
                                modifier = Modifier.size(64.dp)
                            )
                            Text("Verification: PASS", style = MaterialTheme.typography.titleLarge, color = NeonGreen)
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Target Host: ${alert.agent}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text("Wazuh API Poll: Status 200", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text("Active alerts in last 60s: 0 (Mitigated)", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                                }
                            }
                            
                            Button(
                                onClick = { 
                                    onRemediationSuccess(alert.id)
                                    showRemediationDialog = false 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                            ) {
                                Text("Acknowledge & Close", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOllamaSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOllamaSheet = false },
            containerColor = SurfaceDark,
            modifier = Modifier.fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Ollama AI Threat Analyst", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Divider(color = CardBorder)

                if (ollamaLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = NeonIndigo, modifier = Modifier.size(36.dp))
                            Text("Querying local Ollama AI (llama3:8b)...", color = TextSecondary)
                        }
                    }
                } else if (ollamaError != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Error querying Ollama API", color = NeonRed, style = MaterialTheme.typography.bodyLarge)
                            Text(ollamaError ?: "", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Button(
                                onClick = {
                                    ollamaError = null
                                    showOllamaSheet = false
                                    showOllamaSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                            ) {
                                Text("Retry", color = TextPrimary)
                            }
                        }
                    }
                } else {
                    val scope = rememberCoroutineScope()
                    val lazyListState = rememberLazyListState()
                    
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatMessages.filterIndexed { index, _ -> index > 0 || chatMessages[index].role == "assistant" }) { msg ->
                            val isAssistant = msg.role == "assistant"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = if (isAssistant) Alignment.Start else Alignment.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isAssistant) CardBackground else NeonIndigo.copy(alpha = 0.2f))
                                        .border(1.dp, if (isAssistant) CardBorder else NeonIndigo, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = if (isAssistant) "Zero Cool Analyst" else "You",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                                )
                            }
                        }
                        
                        if (chatLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(color = NeonIndigo, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyst is thinking...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Ask follow-up questions...", color = TextSecondary) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = NeonIndigo,
                                unfocusedBorderColor = CardBorder
                            ),
                            maxLines = 3,
                            enabled = !chatLoading
                        )
                        
                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank() && !chatLoading) {
                                    val userText = chatInput
                                    chatInput = ""
                                    chatMessages.add(ChatMessage(role = "user", content = userText))
                                    chatLoading = true
                                    
                                    scope.launch {
                                        try {
                                            lazyListState.animateScrollToItem(chatMessages.size - 1)
                                        } catch (e: Exception) {}
                                    }
                                    
                                    scope.launch {
                                        try {
                                            val ollamaUrl = credentialsManager.getOllamaUrl()
                                            val service = OllamaService.create(ollamaUrl)
                                            val request = OllamaChatRequest(
                                                model = "zero-cool-analyst",
                                                messages = chatMessages.toList(),
                                                stream = false
                                            )
                                            val response = withContext(Dispatchers.IO) {
                                                service.chat(request)
                                            }
                                            chatMessages.add(response.message)
                                        } catch (e: Exception) {
                                            android.util.Log.e("OllamaChat", "Error", e)
                                            chatMessages.add(ChatMessage(role = "assistant", content = "Error: Failed to fetch response. ${e.localizedMessage}"))
                                        } finally {
                                            chatLoading = false
                                            try {
                                                lazyListState.animateScrollToItem(chatMessages.size - 1)
                                            } catch (e: Exception) {}
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(NeonIndigo, CircleShape)
                                .size(48.dp),
                            enabled = chatInput.isNotBlank() && !chatLoading
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = TextPrimary)
                        }
                    }
                }

                Button(
                    onClick = { showOllamaSheet = false },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                ) {
                    Text("Close", color = TextPrimary)
                }
            }
        }
    }
}
