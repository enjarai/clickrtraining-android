package dev.chocobo.clickertraining

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.media.MediaPlayer
import android.os.Build
import okhttp3.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import android.content.pm.PackageManager
import java.io.IOException
import android.os.Handler
import android.os.Looper


class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_POST_NOTIFICATIONS = 101

    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private lateinit var mediaPlayer: MediaPlayer
    private var isListening = false

    private val targetChar = "c"

    private lateinit var stopListeningReceiver: BroadcastReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val listenId = findViewById<EditText>(R.id.idText)
        val listenButton = findViewById<Button>(R.id.listenButton)
        val clickButton = findViewById<Button>(R.id.clickButton)

        clickButton.setOnClickListener {
            val id = listenId.text.toString().trim()
            if (id.isEmpty()) return@setOnClickListener

            val url = "https://clickertrain.ing/api/$id/click"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        // Handle failure (toast, log, etc.)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            runOnUiThread {
                                // Handle unsuccessful response
                            }
                        } else {
                            runOnUiThread {
                                // Handle success (toast, update UI, etc.)
                            }
                        }
                    }
                }
            })
        }

        stopListeningReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "dev.chocobo.clickertraining.ACTION_STOPPED") {
                    runOnUiThread {
                        val listenButton = findViewById<Button>(R.id.listenButton)
                        listenButton.text = "Listen"
                        isListening = false
                    }
                }
            }
        }

        val filter = IntentFilter("dev.chocobo.clickertraining.ACTION_STOPPED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33
            registerReceiver(
                stopListeningReceiver,
                filter,
                null,
                Handler(Looper.getMainLooper()),  // pass a Handler instead of Executor
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(stopListeningReceiver, filter)
        }




        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }
        }

        mediaPlayer = MediaPlayer.create(this, R.raw.static_sound)
        // Create DoH client using Cloudflare or Google resolver
        val bootstrapClient = OkHttpClient.Builder().build()

        val doh = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())  // Cloudflare DoH endpoint
            .bootstrapDnsHosts(listOf(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            ))
            .build()

        // Then build your OkHttp client with this DNS
        client = OkHttpClient.Builder()
            .dns(doh)
            .build()

        listenButton.setOnClickListener {
            val id = listenId.text.toString().trim()
            if (id.isEmpty()) return@setOnClickListener

            if (!isListening) {
                val intent = Intent(this, WebSocketService::class.java).apply {
                    putExtra(WebSocketService.EXTRA_ID, id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                listenButton.text = "Stop Listening"
                isListening = true
            } else {
                val stopIntent = Intent(this, WebSocketService::class.java).apply {
                    action = WebSocketService.ACTION_STOP
                }
                startService(stopIntent)
                listenButton.text = "Listen"
                isListening = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.cancel()
        client.dispatcher.executorService.shutdown()
        try {
            unregisterReceiver(stopListeningReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered or already unregistered, ignore
        }
    }

}