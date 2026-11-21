package com.bothost.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.rgb(8, 12, 25)
    private val panel = Color.rgb(17, 26, 46)
    private val panel2 = Color.rgb(23, 35, 59)
    private val cyan = Color.rgb(62, 213, 255)
    private val purple = Color.rgb(160, 112, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(145, 162, 190)

    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10
            )
        }

        buildDashboard()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(label("B O T H O S T", 22f, cyan, true))
        brand.addView(label("ANDROID HOSTING CONTROL CENTER", 10f, muted))

        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val version = label("ALPHA 0.1", 10f, purple, true)
        version.setPadding(dp(12), dp(8), dp(12), dp(8))
        version.background = rounded(panel2, 30)
        header.addView(version)

        content.addView(header)
        content.addView(space(24))

        // Main welcome card
        val hero = card(panel2).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        hero.addView(label("YOUR BOT SERVER", 11f, muted, true))
        hero.addView(space(8))
        hero.addView(label("Everything in one place.", 23f, white, true))
        hero.addView(space(6))
        hero.addView(
            label("Manage your Discord bots directly from your phone.", 13f, muted)
        )
        hero.addView(space(18))

        val onlineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = label("●", 14f, Color.rgb(255, 184, 75))
        onlineRow.addView(dot)
        onlineRow.addView(spaceHorizontal(8))

        serviceStatus = label("HOST SERVICE STOPPED", 11f, white, true)
        onlineRow.addView(serviceStatus)

        hero.addView(onlineRow)
        content.addView(hero)

        content.addView(space(22))
        content.addView(label("OVERVIEW", 13f, muted, true))
        content.addView(space(10))

        // Stats
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        stats.addView(
            statCard("0", "RUNNING BOTS", cyan),
            LinearLayout.LayoutParams(0, dp(105), 1f)
        )

        val gap = View(this)
        stats.addView(gap, LinearLayout.LayoutParams(dp(12), 1))

        stats.addView(
            statCard("0", "IMPORTED BOTS", purple),
            LinearLayout.LayoutParams(0, dp(105), 1f)
        )

        content.addView(stats)
        content.addView(space(22))

        // Host service controls
        content.addView(label("HOST CONTROLS", 13f, muted, true))
        content.addView(space(10))

        val controls = card(panel).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        controls.addView(label("Hosting service", 17f, white, true))
        controls.addView(space(5))
        controls.addView(
            label("Start the background service to prepare BotHost.", 12f, muted)
        )
        controls.addView(space(16))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val start = actionButton("START HOST", cyan)
        start.setOnClickListener {
            try {
                val intent = Intent(this@MainActivity, BotHostService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                serviceStatus.text = "HOST SERVICE STARTED"
                Toast.makeText(this, "Host service started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Could not start service: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val stop = actionButton("STOP HOST", purple)
        stop.setOnClickListener {
            stopService(Intent(this@MainActivity, BotHostService::class.java))
            serviceStatus.text = "HOST SERVICE STOPPED"
            Toast.makeText(this, "Host service stopped", Toast.LENGTH_SHORT).show()
        }

        buttons.addView(start, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(spaceHorizontal(10))
        buttons.addView(stop, LinearLayout.LayoutParams(0, dp(48), 1f))

        controls.addView(buttons)
        content.addView(controls)

        content.addView(space(22))

        // Bots section
        val botsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        botsHeader.addView(
            label("YOUR BOTS", 13f, muted, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val add = actionButton("+ ADD BOT", cyan)
        add.setOnClickListener {
            Toast.makeText(
                this@MainActivity,
                "Bot importing is coming in the next development step.",
                Toast.LENGTH_LONG
            ).show()
        }
        botsHeader.addView(add)

        content.addView(botsHeader)
        content.addView(space(10))

        val empty = card(panel).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(25), dp(18), dp(25))
        }

        empty.addView(label("◈", 34f, cyan, true))
        empty.addView(space(8))
        empty.addView(label("No bots imported yet", 16f, white, true))
        empty.addView(space(6))
        empty.addView(
            label("Your Python and JavaScript bots will appear here.", 12f, muted)
        )

        content.addView(empty)
        content.addView(space(22))

        // Console preview
        content.addView(label("CONSOLE", 13f, muted, true))
        content.addView(space(10))

        val console = card(Color.rgb(7, 11, 20)).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        console.addView(label("BOT HOST TERMINAL", 11f, cyan, true))
        console.addView(space(10))
        console.addView(label("> BotHost dashboard initialized", 11f, white))
        console.addView(label("> Python runtime: configured", 11f, muted))
        console.addView(label("> Node.js runtime: not installed", 11f, muted))
        console.addView(label("> Waiting for bot manager...", 11f, purple))

        content.addView(console)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setBackgroundColor(Color.rgb(12, 19, 34))
        }

        listOf("⌂\nHOME", "▤\nBOTS", ">_\nCONSOLE", "⚙\nSETTINGS").forEachIndexed { index, name ->
            val item = label(
                name,
                11f,
                if (index == 0) cyan else muted,
                index == 0
            )
            item.gravity = Gravity.CENTER
            item.setPadding(dp(8), dp(4), dp(8), dp(4))
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))

            item.setOnClickListener {
                val section = listOf("Home", "Bots", "Console", "Settings")[index]
                Toast.makeText(
                    this@MainActivity,
                    "$section section is being developed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(nav)
        setContentView(root)
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun card(color: Int): LinearLayout {
        return LinearLayout(this).apply {
            background = rounded(color, 20)
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(37, 52, 77))
        }
    }

    private fun actionButton(title: String, color: Int): Button {
        return Button(this).apply {
            text = title
            textSize = 11f
            setTextColor(bg)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = rounded(color, 14)
            stateListAnimator = null
        }
    }

    private fun statCard(number: String, caption: String, color: Int): LinearLayout {
        return card(panel).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(12), dp(12), dp(12))
            addView(label(number, 27f, color, true))
            addView(space(3))
            addView(label(caption, 10f, muted, true))
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }
    }

    private fun spaceHorizontal(width: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(width), 1)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
