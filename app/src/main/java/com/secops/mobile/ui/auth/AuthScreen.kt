package com.secops.mobile.ui.auth

import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.secops.mobile.data.security.CredentialsManager
import com.secops.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onNavigateToDashboard: () -> Unit) {
    val context = LocalContext.current
    val credentialsManager = remember { CredentialsManager(context) }
    
    var wazuhUrl by remember { mutableStateOf(credentialsManager.getWazuhUrl()) }
    var ollamaUrl by remember { mutableStateOf(credentialsManager.getOllamaUrl()) }
    var showSsoWebView by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SIEM Connect Gateway", style = MaterialTheme.typography.titleLarge) },
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Secure Connection",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Authenticate via Authelia Single Sign-On",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Glassmorphic Input Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = wazuhUrl,
                            onValueChange = { wazuhUrl = it },
                            label = { Text("Wazuh Manager URL", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonIndigo,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = NeonIndigo,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = ollamaUrl,
                            onValueChange = { ollamaUrl = it },
                            label = { Text("Ollama API URL", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonIndigo,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = NeonIndigo,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Glowing SSO Button
                Button(
                    onClick = { showSsoWebView = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                ) {
                    Text("Sign In with SSO (Authelia)", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Full screen overlay layout to prevent Dialog measuring collapse bugs
        if (showSsoWebView) {
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
                        Text("Authelia Single Sign-On", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        TextButton(onClick = { showSsoWebView = false }) {
                            Text("Cancel", color = NeonRed)
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
                                        handler?.proceed()
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        url?.let { currentUrl ->
                                            // Only capture session when we are NOT on the Authelia login portal (auth.example.com)
                                            if (currentUrl.contains("auth.example.com") || currentUrl.contains("auth.")) {
                                                return
                                            }
                                            
                                            val cookieManager = CookieManager.getInstance()
                                            val cookies = cookieManager.getCookie(currentUrl)
                                            if (cookies != null && cookies.contains("authelia_session")) {
                                                val sessionCookie = cookies.split(";").find { c ->
                                                    c.trim().startsWith("authelia_session=")
                                                }?.trim()
                                                
                                                if (sessionCookie != null) {
                                                    credentialsManager.saveAutheliaSession(sessionCookie)
                                                    credentialsManager.saveWazuhUrl(wazuhUrl)
                                                    credentialsManager.saveOllamaUrl(ollamaUrl)
                                                    
                                                    showSsoWebView = false
                                                    onNavigateToDashboard()
                                                }
                                            }
                                        }
                                    }
                                }
                                loadUrl("https://wazuh.example.com")
                            }
                        }
                    )
                }
            }
        }
    }
}
