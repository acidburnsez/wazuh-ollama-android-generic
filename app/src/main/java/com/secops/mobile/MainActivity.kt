package com.secops.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.secops.mobile.ui.agents.AgentsScreen
import com.secops.mobile.ui.auth.AuthScreen
import com.secops.mobile.ui.dashboard.DashboardScreen
import com.secops.mobile.ui.dashboard.MainScreen
import com.secops.mobile.ui.details.AlertDetailsScreen
import com.secops.mobile.ui.feed.AlertFeedAndDetailsScreen
import com.secops.mobile.ui.feed.MockAlert
import com.secops.mobile.ui.navigation.Screen
import com.secops.mobile.ui.theme.SecOpsMobileTheme
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.data.api.WazuhService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Intent
import android.os.Build
import com.secops.mobile.service.GotifyNotificationService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request post notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Start background Gotify push listener
        try {
            val serviceIntent = Intent(this, GotifyNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("Gotify", "Failed to start Gotify background service", e)
        }

        setContent {
            SecOpsMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val credentialsManager = remember { CredentialsManager(this@MainActivity) }
                    val coroutineScope = rememberCoroutineScope()

                    // Lifted alert lists database states
                    val alertsList = remember {
                        mutableStateListOf(
                            MockAlert("1001", 12, "AppArmor DENIED operation=mount", "ubuntu-host", "04:31:02"),
                            MockAlert("1002", 9, "Trojan rootcheck: Hidden files found in /dev/.lxc", "plex-lxc", "04:28:45"),
                            MockAlert("1003", 5, "Wazuh Agent connected", "ollama-ct", "04:25:10"),
                            MockAlert("1004", 15, "Multiple SSH authentication failures (Brute Force)", "opnsense-gateway", "04:12:00"),
                            MockAlert("1005", 3, "Web server access log: 200 OK", "personal-web", "04:00:55")
                        )
                    }

                    fun fetchWazuhAlerts() {
                        coroutineScope.launch {
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
                                
                                val alertsResponse = try {
                                    withContext(Dispatchers.IO) {
                                        service.getAlerts(bearerToken = "Bearer $token", limit = 50, minLevel = 5)
                                    }
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 401) {
                                        // Token expired/invalid, clear and retry once
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
                                            service.getAlerts(bearerToken = "Bearer $token", limit = 50, minLevel = 5)
                                        }
                                    } else {
                                        throw e
                                    }
                                }
                                
                                val items = alertsResponse.data.affectedItems
                                alertsList.clear()
                                items.forEach { item ->
                                    val timeStr = try {
                                        if (item.timestamp.contains("T")) {
                                            item.timestamp.substringAfter("T").substring(0, 8)
                                        } else {
                                            item.timestamp
                                        }
                                    } catch (e: Exception) {
                                        "00:00"
                                    }
                                    alertsList.add(
                                        MockAlert(
                                            id = item.id,
                                            level = item.rule.level,
                                            description = item.rule.description,
                                            agent = item.agent.name,
                                            time = timeStr
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Wazuh", "Error fetching alerts", e)
                                if (e is retrofit2.HttpException && e.code() == 401) {
                                    credentialsManager.saveWazuhToken(null)
                                }
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        fetchWazuhAlerts()
                    }

                    val remediatedAlertIds = remember { mutableStateOf(setOf<String>()) }
                    var hideRemediated by remember { mutableStateOf(false) }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Auth.route
                    ) {
                        composable(Screen.Auth.route) {
                            AuthScreen(
                                onNavigateToDashboard = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Auth.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Dashboard.route) {
                            MainScreen(
                                onNavigateToAlerts = {
                                    navController.navigate(Screen.AlertFeed.route)
                                },
                                onNavigateToAgents = {
                                    navController.navigate(Screen.Agents.route)
                                },
                                onSignOut = {
                                    navController.navigate(Screen.Auth.route) {
                                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.AlertFeed.route) {
                            AlertFeedAndDetailsScreen(
                                alerts = alertsList,
                                remediatedAlertIds = remediatedAlertIds.value,
                                hideRemediated = hideRemediated,
                                onToggleHideRemediated = { hideRemediated = it },
                                onRefresh = {
                                    fetchWazuhAlerts()
                                },
                                onDismissAlert = { id ->
                                    alertsList.removeIf { it.id == id }
                                },
                                onRemediationSuccess = { id ->
                                    remediatedAlertIds.value = remediatedAlertIds.value + id
                                },
                                onNavigateToDetails = { alertId ->
                                    navController.navigate(Screen.AlertDetails.createRoute(alertId))
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(Screen.Agents.route) {
                            AgentsScreen()
                        }
                        composable(Screen.AlertDetails.route) { backStackEntry ->
                            val alertId = backStackEntry.arguments?.getString("alertId") ?: "1001"
                            val alert = alertsList.find { it.id == alertId } ?: MockAlert("1001", 12, "AppArmor DENIED", "unknown", "00:00")
                            AlertDetailsScreen(
                                alert = alert,
                                onRemediationSuccess = { id ->
                                    remediatedAlertIds.value = remediatedAlertIds.value + id
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
