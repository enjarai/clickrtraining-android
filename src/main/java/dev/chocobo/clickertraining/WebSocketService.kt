package dev.chocobo.clickertraining

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.media.MediaPlayer
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

class WebSocketService : Service() {

    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private lateinit var mediaPlayer: MediaPlayer

    companion object {
        const val CHANNEL_ID = "ListeningServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "dev.chocobo.clickertraining.ACTION_STOP"
        const val EXTRA_ID = "id"
    }

    override fun onCreate() {
        super.onCreate()

        mediaPlayer = MediaPlayer.create(this, R.raw.static_sound)

        val bootstrapClient = OkHttpClient.Builder().build()
        val doh = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            ))
            .build()

        client = OkHttpClient.Builder()
            .dns(doh)
            .build()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        val id = intent?.getStringExtra(EXTRA_ID)
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopListening()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (id != null) {
            startListening(id)
        }

        return START_STICKY
    }


    private fun startListening(id: String) {
        val url = "wss://clickertrain.ing/api/$id/listen"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("WebSocketService", "Connected")

                // Save listening state in SharedPreferences
                val prefs = getSharedPreferences("clicker_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("is_listening", true)
                    .putString("saved_id", id)
                    .apply()

                // Broadcast that listening has started
                val intent = Intent("dev.chocobo.clickertraining.ACTION_STARTED")
                sendBroadcast(intent)
            }
        })
    }

    private fun stopListening() {
        webSocket?.close(1000, "User stopped listening")
        webSocket = null
        mediaPlayer.stop()
        mediaPlayer.reset()
        notifyActivityStopped()

        // Clear listening state from SharedPreferences
        val prefs = getSharedPreferences("clicker_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_listening", false)
            .apply()
    }

    private fun notifyActivityStopped() {
        val intent = Intent("dev.chocobo.clickertraining.ACTION_STOPPED")
        sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, WebSocketService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Listening for a click")
            .setContentText("WebSocket is active")
            .addAction(
                android.R.drawable.ic_delete,
                "Stop Listening",
                stopPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Listening Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}
