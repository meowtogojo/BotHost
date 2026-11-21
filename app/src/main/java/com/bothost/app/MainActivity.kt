package com.bothost.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        val title = TextView(this).apply {
            text = "BotHost"
            textSize = 28f
        }
        val subtitle = TextView(this).apply {
            text = "Local Discord bot hosting • Python + Node.js"
            textSize = 14f
        }
        val status = TextView(this).apply {
            text = "Host service: stopped\nRuntime setup: development build"
            textSize = 16f
        }
        val start = Button(this).apply {
            text = "Start host service"
            setOnClickListener {
                val i = Intent(this@MainActivity, BotHostService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                status.text = "Host service: running\nNo bot started yet."
            }
        }
        val stop = Button(this).apply {
            text = "Stop host service"
            setOnClickListener {
                stopService(Intent(this@MainActivity, BotHostService::class.java))
                status.text = "Host service: stopped"
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
            addView(title)
            addView(subtitle)
            addView(status)
            addView(start)
            addView(stop)
        }
        setContentView(layout)
    }
}
