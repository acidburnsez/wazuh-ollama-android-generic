package com.secops.mobile.ui.dashboard

import android.webkit.CookieManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.ui.theme.*

enum class HomelabTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Overview("Overview", Icons.Default.Home),
    Infrastructure("Infrastructure", Icons.Default.Build),
    Alerts("Alerts", Icons.Default.Warning),
    Security("Security", Icons.Default.Lock),
    Portal("Portal", Icons.Default.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToAlerts: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    var currentTab by remember { mutableStateOf(HomelabTab.Overview) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Homelab Center", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(
                        onClick = {
                            credentialsManager.saveAutheliaSession(null)
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            onSignOut()
                        }
                    ) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepObsidian,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextSecondary
            ) {
                HomelabTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        label = { Text(tab.title, color = if (currentTab == tab) NeonIndigo else TextSecondary) },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                tint = if (currentTab == tab) NeonIndigo else TextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = CardBorder
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                HomelabTab.Overview -> OverviewScreen()
                HomelabTab.Infrastructure -> InfrastructureScreen()
                HomelabTab.Alerts -> AlertManagerScreen()
                HomelabTab.Security -> DashboardScreen(
                    onNavigateToAlerts = onNavigateToAlerts,
                    onNavigateToAgents = onNavigateToAgents,
                    onSignOut = onSignOut
                )
                HomelabTab.Portal -> PortalScreen()
            }
        }
    }
}
