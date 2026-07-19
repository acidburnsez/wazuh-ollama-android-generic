package com.secops.mobile.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.secops.mobile.ui.theme.*

data class MockAlert(val id: String, val level: Int, val description: String, val agent: String, val time: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertFeedScreen(
    alerts: List<MockAlert>,
    remediatedAlertIds: Set<String>,
    hideRemediated: Boolean,
    onToggleHideRemediated: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDismissAlert: (String) -> Unit,
    onNavigateToDetails: (String) -> Unit,
    onNavigateBack: () -> Unit,
    isSplitMode: Boolean = false
) {
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    // Filter alerts locally based on toggle state
    val displayedAlerts = remember(alerts, remediatedAlertIds, hideRemediated) {
        if (hideRemediated) {
            alerts.filter { it.id !in remediatedAlertIds }
        } else {
            alerts
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts Feed", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (!isSplitMode) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            onRefresh()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Feed", tint = TextPrimary)
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
                .padding(horizontal = 16.dp)
        ) {
            // Filter section at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = hideRemediated,
                    onClick = { onToggleHideRemediated(!hideRemediated) },
                    label = { Text("Hide Mitigated", color = TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonIndigo,
                        containerColor = CardBackground
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = CardBorder
                    )
                )

                Text(
                    text = "${displayedAlerts.size} Alerts",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }

            if (isRefreshing) {
                LinearProgressIndicator(
                    color = NeonIndigo,
                    trackColor = CardBackground,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                // Simulate refresh completion
                LaunchedEffect(Unit) {
                    kotlin.concurrent.timer(initialDelay = 1200L, period = 1200L) {
                        isRefreshing = false
                        this.cancel()
                    }
                }
            }

            if (displayedAlerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active alerts", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(displayedAlerts, key = { index, alert -> "${alert.id}_$index" }) { index, alert ->
                        val isRemediated = alert.id in remediatedAlertIds
                        val levelColor = when {
                            isRemediated -> TextMuted
                            alert.level >= 12 -> NeonRed
                            alert.level >= 8 -> NeonOrange
                            else -> NeonGreen
                        }

                        val dismissState = rememberDismissState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == DismissValue.DismissedToEnd || dismissValue == DismissValue.DismissedToStart) {
                                    onDismissAlert(alert.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismiss(
                            state = dismissState,
                            directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
                            background = {
                                val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
                                val color = if (direction == DismissDirection.StartToEnd) NeonGreen.copy(alpha = 0.2f) else NeonRed.copy(alpha = 0.2f)
                                val border = if (direction == DismissDirection.StartToEnd) NeonGreen else NeonRed
                                val align = if (direction == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                val text = if (direction == DismissDirection.StartToEnd) "Mitigate" else "Dismiss"
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, RoundedCornerShape(12.dp))
                                        .border(1.dp, border, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = align
                                ) {
                                    Text(text = text, color = border, style = MaterialTheme.typography.titleSmall)
                                }
                            },
                            dismissContent = {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                        .clickable { onNavigateToDetails(alert.id) },
                                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Severity Indicator Strip
                                        Box(
                                            modifier = Modifier
                                                .width(6.dp)
                                                .height(56.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(levelColor)
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Rule: ${alert.id}", style = MaterialTheme.typography.titleMedium, color = if (isRemediated) TextMuted else TextPrimary)
                                                Box(
                                                    modifier = Modifier
                                                        .background(levelColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, levelColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (isRemediated) "Mitigated" else "Lvl ${alert.level}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = levelColor
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(alert.description, style = MaterialTheme.typography.bodyLarge, color = if (isRemediated) TextMuted else TextPrimary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Agent: ${alert.agent} | Time: ${alert.time}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
