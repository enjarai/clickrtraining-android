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
import android.text.Editable
import android.text.TextWatcher


class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_POST_NOTIFICATIONS = 101

    private lateinit var client: OkHttpClient
    private lateinit var mediaPlayer: MediaPlayer
    private var isListening = false

    private val targetChar = "c"

    private lateinit var stateReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val listenId = findViewById<EditText>(R.id.idText)
        val listenButton = findViewById<Button>(R.id.listenButton)
        val clickButton = findViewById<Button>(R.id.clickButton)
        val prefs = getSharedPreferences("clicker_prefs", Context.MODE_PRIVATE)

        // Restore saved ID and listening state
        listenId.setText(prefs.getString("saved_id", ""))
        isListening = prefs.getBoolean("is_listening", false)

        // Update button text based on listening state
        listenButton.text = if (isListening) "Stop Listening" else "Listen"

        // Register BroadcastReceiver for start/stop listening events
        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "dev.chocobo.clickertraining.ACTION_STARTED" -> {
                        isListening = true
                        listenButton.text = "Stop Listening"
                        prefs.edit().putBoolean("is_listening", true).apply()
                    }
                    "dev.chocobo.clickertraining.ACTION_STOPPED" -> {
                        isListening = false
                        listenButton.text = "Listen"
                        prefs.edit().putBoolean("is_listening", false).apply()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("dev.chocobo.clickertraining.ACTION_STARTED")
            addAction("dev.chocobo.clickertraining.ACTION_STOPPED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            registerReceiver(
                stateReceiver,
                filter,
                null,
                Handler(Looper.getMainLooper()),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(stateReceiver, filter)
        }

        // If was listening, start (or re-attach) service to maintain connection
        if (isListening) {
            val id = listenId.text.toString().trim()
            if (id.isNotEmpty()) {
                val intent = Intent(this, WebSocketService::class.java).apply {
                    putExtra(WebSocketService.EXTRA_ID, id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                isListening = false
                prefs.edit().putBoolean("is_listening", false).apply()
                listenButton.text = "Listen"
            }
        }

        // Save ID when it changes
        listenId.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString("saved_id", s.toString()).apply()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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
            prefs.edit().putBoolean("is_listening", isListening).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.dispatcher.executorService.shutdown()
        try {
            unregisterReceiver(stateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered or already unregistered, ignore
        }
    }

}

