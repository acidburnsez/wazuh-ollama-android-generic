package com.secops.mobile.ui.dashboard

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.secops.mobile.ui.theme.*

data class PortalShortcut(val name: String, val url: String, val iconLabel: String)

@Composable
fun PortalScreen() {
    var activeUrl by remember { mutableStateOf<String?>(null) }

    val shortcuts = remember {
        listOf(
            PortalShortcut("Homepage", "https://homepage.example.com", "🏠"),
            PortalShortcut("Uptime Monitor", "https://status.example.com", "🚦"),
            PortalShortcut("Grafana Metrics", "https://grafana.example.com", "📈"),
            PortalShortcut("Paperless", "https://paperless.example.com", "📂"),
            PortalShortcut("Gitea Server", "https://gitea.example.com", "📦"),
            PortalShortcut("Overseerr", "https://overseerr.example.com", "🍿"),
            PortalShortcut("Requestrr", "https://requestrr.example.com", "🤖"),
            PortalShortcut("Plex Server", "https://plex.example.com", "🎬"),
            PortalShortcut("Wazuh Portal", "https://wazuh.example.com", "🛡️")
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepObsidian)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text(
                    text = "Homelab Portal",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Direct access to all local web interfaces",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(shortcuts.size) { i ->
                    val shortcut = shortcuts[i]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable { activeUrl = shortcut.url },
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(shortcut.iconLabel, style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                shortcut.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Full screen webview overlay
        activeUrl?.let { url ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DeepObsidian
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val title = url.substringAfter("https://").substringBefore(".example.com").uppercase()
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        TextButton(onClick = { activeUrl = null }) {
                            Text("Close", color = NeonRed)
                        }
                    }

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: SslErrorHandler?,
                                        error: SslError?
                                    ) {
                                        handler?.proceed() // Proceed past local certs
                                    }
                                }
                                loadUrl(url)
                            }
                        }
                    )
                }
            }
        }
    }
}
