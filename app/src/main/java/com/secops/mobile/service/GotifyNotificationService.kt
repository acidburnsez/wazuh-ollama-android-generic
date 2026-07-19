package com.secops.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.secops.mobile.MainActivity
import com.secops.mobile.data.security.CredentialsManager
import okhttp3.*
import java.util.concurrent.TimeUnit

class GotifyNotificationService : Service() {

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private lateinit var credentialsManager: CredentialsManager
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        credentialsManager = CredentialsManager(this)
        createNotificationChannel()
        startWebSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create FGS channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fgsChannel = NotificationChannel(
                FGS_CHANNEL_ID,
                "SecOps Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background WebSocket paging daemon status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(fgsChannel)
        }

        val notification = NotificationCompat.Builder(this, FGS_CHANNEL_ID)
            .setContentTitle("SecOps Active Shield")
            .setContentText("Listening for security threats & telemetry...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun closeWebSocket() {
        try {
            webSocket?.close(1000, "Service restarting/reconnecting")
        } catch (e: Exception) {}
        webSocket = null
        try {
            client?.dispatcher?.executorService?.shutdown()
        } catch (e: Exception) {}
        client = null
    }

    private fun startWebSocket() {
        closeWebSocket()

        val gotifyUrl = credentialsManager.getGotifyUrl()
        val token = credentialsManager.getGotifyToken()

        val wsUrl = gotifyUrl.replace("http://", "ws://")
            .replace("https://", "wss://")
            .let { if (it.endsWith("/")) it else "$it/" } + "stream?token=$token"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive & detect silent drops
            .hostnameVerifier { _, _ -> true }
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                android.util.Log.d("Gotify", "WebSocket received message: $text")
                try {
                    val message = gson.fromJson(text, GotifyMessage::class.java)
                    showNotification(message.title, message.message, message.priority)
                } catch (e: Exception) {
                    android.util.Log.e("Gotify", "Error parsing message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("Gotify", "WebSocket failure, retrying in 5 seconds...", t)
                closeWebSocket()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startWebSocket()
                }, 5000)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.d("Gotify", "WebSocket closed: $reason. Reconnecting...")
                closeWebSocket()
                startWebSocket()
            }
        })
    }

    private fun showNotification(title: String, message: String, priority: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val importance = when {
            priority >= 8 -> NotificationCompat.PRIORITY_MAX
            priority >= 4 -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(importance)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gotify Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time security alert feeds from Gotify"
                enableLights(true)
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service destroyed")
    }

    companion object {
        private const val CHANNEL_ID = "gotify_alerts_channel"
        private const val FGS_CHANNEL_ID = "secops_monitor_channel"
    }

    private data class GotifyMessage(
        val id: Int,
        val appid: Int,
        val message: String,
        val title: String,
        val priority: Int
    )
}
