package com.secops.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.secops.mobile.ui.details.AlertDetailsScreen
import com.secops.mobile.ui.theme.*

@Composable
fun AlertFeedAndDetailsScreen(
    alerts: List<MockAlert>,
    remediatedAlertIds: Set<String>,
    hideRemediated: Boolean,
    onToggleHideRemediated: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDismissAlert: (String) -> Unit,
    onRemediationSuccess: (String) -> Unit,
    onNavigateToDetails: (String) -> Unit,
    onNavigateBack: () -> Unit,
    initialAlertId: String? = null
) {
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600

    if (isLargeScreen) {
        var selectedAlertId by remember { mutableStateOf(initialAlertId ?: "1001") }
        val currentAlert = alerts.find { it.id == selectedAlertId } ?: alerts.firstOrNull() ?: MockAlert("1001", 12, "AppArmor DENIED", "unknown", "00:00")
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepObsidian)
        ) {
            // Left Pane (Alert Feed)
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                AlertFeedScreen(
                    alerts = alerts,
                    remediatedAlertIds = remediatedAlertIds,
                    hideRemediated = hideRemediated,
                    onToggleHideRemediated = onToggleHideRemediated,
                    onRefresh = onRefresh,
                    onDismissAlert = onDismissAlert,
                    onNavigateToDetails = { id -> selectedAlertId = id },
                    onNavigateBack = onNavigateBack,
                    isSplitMode = true
                )
            }
            
            // Vertical Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(CardBorder)
            )
            
            // Right Pane (Alert Log Details + Interactive Remediation)
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                AlertDetailsScreen(
                    alert = currentAlert,
                    onRemediationSuccess = onRemediationSuccess,
                    onNavigateBack = {},
                    isSplitMode = true
                )
            }
        }
    } else {
        // Fallback for cover screen / standard portrait view
        AlertFeedScreen(
            alerts = alerts,
            remediatedAlertIds = remediatedAlertIds,
            hideRemediated = hideRemediated,
            onToggleHideRemediated = onToggleHideRemediated,
            onRefresh = onRefresh,
            onDismissAlert = onDismissAlert,
            onNavigateToDetails = onNavigateToDetails,
            onNavigateBack = onNavigateBack,
            isSplitMode = false
        )
    }
}
