package com.bothost.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val zipPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bot = BotManager.importZip(this, uri)
                Toast.makeText(
                    this,
                    "Imported ${bot.name} (${bot.runtime})",
                    Toast.LENGTH_LONG
                ).show()
                buildDashboard()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Import failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val bg = Color.rgb(8, 12, 25)
    private val panel = Color.rgb(17, 26, 46)
    private val panel2 = Color.rgb(23, 35, 59)
    private val cyan = Color.rgb(62, 213, 255)
    private val purple = Color.rgb(160, 112, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(145, 162, 190)
    private val green = Color.rgb(75, 220, 145)
    private val red = Color.rgb(255, 100, 110)

    private lateinit var serviceStatus: TextView
    private lateinit var runningCount: TextView
    private lateinit var importedCount: TextView

    private val statusViews = mutableMapOf<String, TextView>()
    private val handler = Handler(Looper.getMainLooper())
    private val statusExecutor = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                10
            )
        }

        buildDashboard()
        startStatusUpdates()
    }

    private fun startServiceCommand(action: String? = null, botId: String? = null) {
        val intent = Intent(this, BotHostService::class.java)

        if (action != null) {
            intent.action = action
        }

        if (botId != null) {
            intent.putExtra(BotHostService.EXTRA_BOT_ID, botId)
        }

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startStatusUpdates() {
        statusExecutor.scheduleWithFixedDelay({
            try {
                val bots = BotManager.getBots(this)

                var running = 0

                val statuses = bots.map { bot ->
                    var details: String? = null

                    val status = try {
                        if (bot.runtime == "Python") {
                            val current = BotRuntime.getStatus(this, bot.id)

                            if (current == "error") {
                                val error = BotRuntime.getError(this, bot.id)
                                val logs = BotRuntime.getLogs(this, bot.id)

                                details = buildString {
                                    append(
                                        error?.takeIf { it.isNotBlank() }
                                            ?: "No Python traceback was recorded."
                                    )

                                    if (logs.isNotBlank()) {
                                        append("\\n\\n--- BOT LOGS ---\\n")
                                        append(logs)
                                    }
                                }
                            }

                            current
                        } else {
                            "unsupported"
                        }
                    } catch (e: Exception) {
                        details = e.stackTraceToString()
                        "error"
                    }

                    if (status == "running") running++

                    Triple(bot.id, status, details)
                }

                handler.post {
                    if (!isFinishing && !isDestroyed) {
                        runningCount.text = running.toString()
                        importedCount.text = bots.size.toString()

                        statuses.forEach { (id, status, details) ->
                            statusViews[id]?.apply {
                                text = "STATUS: ${status.uppercase()}"

                                setTextColor(
                                    when (status) {
                                        "running" -> green
                                        "error", "unsupported" -> red
                                        "starting", "stopping" -> purple
                                        else -> muted
                                    }
                                )

                                isClickable = status == "error"

                                setOnClickListener {
                                    if (status == "error") {
                                        AlertDialog.Builder(this@MainActivity)
                                            .setTitle("Bot startup error")
                                            .setMessage(
                                                details
                                                    ?: "No error details available."
                                            )
                                            .setPositiveButton("CLOSE", null)
                                            .show()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep dashboard alive during temporary status failures.
            }
        }, 0, 2, TimeUnit.SECONDS)
    }

    private fun buildDashboard() {
        statusViews.clear()

        val importedBots = BotManager.getBots(this)

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

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val version = label("ALPHA 0.1", 10f, purple, true)
        version.setPadding(dp(12), dp(8), dp(12), dp(8))
        version.background = rounded(panel2, 30)
        header.addView(version)

        content.addView(header)
        content.addView(space(24))

        // Welcome card
        val hero = card(panel2).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        hero.addView(label("YOUR BOT SERVER", 11f, muted, true))
        hero.addView(space(8))
        hero.addView(label("Everything in one place.", 23f, white, true))
        hero.addView(space(6))
        hero.addView(
            label(
                "Manage your Discord bots directly from your phone.",
                13f,
                muted
            )
        )
        hero.addView(space(18))

        val onlineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        onlineRow.addView(label("●", 14f, green))
        onlineRow.addView(spaceHorizontal(8))

        serviceStatus = label("BOT HOST CONTROL CENTER", 11f, white, true)
        onlineRow.addView(serviceStatus)

        hero.addView(onlineRow)
        content.addView(hero)
        content.addView(space(22))

        // Overview
        content.addView(label("OVERVIEW", 13f, muted, true))
        content.addView(space(10))

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val runningCard = statCard("0", "RUNNING BOTS", cyan)
        runningCount = runningCard.getChildAt(0) as TextView

        val importedCard = statCard(
            importedBots.size.toString(),
            "IMPORTED BOTS",
            purple
        )
        importedCount = importedCard.getChildAt(0) as TextView

        stats.addView(
            runningCard,
            LinearLayout.LayoutParams(0, dp(105), 1f)
        )

        stats.addView(spaceHorizontal(12))

        stats.addView(
            importedCard,
            LinearLayout.LayoutParams(0, dp(105), 1f)
        )

        content.addView(stats)
        content.addView(space(22))

        // Host controls
        content.addView(label("HOST CONTROLS", 13f, muted, true))
        content.addView(space(10))

        val controls = card(panel).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        controls.addView(label("Hosting service", 17f, white, true))
        controls.addView(space(5))
        controls.addView(
            label(
                "Manage your background bot-hosting service.",
                12f,
                muted
            )
        )
        controls.addView(space(16))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val start = actionButton("START HOST", cyan)
        start.setOnClickListener {
            try {
                startServiceCommand()
                serviceStatus.text = "HOST SERVICE START REQUESTED"
                Toast.makeText(
                    this,
                    "Host service start requested",
                    Toast.LENGTH_SHORT
                ).show()
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
            try {
                startServiceCommand(BotHostService.ACTION_STOP_HOST)
                serviceStatus.text = "HOST STOP REQUESTED"
                Toast.makeText(
                    this,
                    "Host stop requested",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Could not stop host: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        buttons.addView(start, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(spaceHorizontal(10))
        buttons.addView(stop, LinearLayout.LayoutParams(0, dp(48), 1f))

        controls.addView(buttons)
        content.addView(controls)
        content.addView(space(22))

        // Bot list header
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
            zipPicker.launch("*/*")
        }

        botsHeader.addView(add)
        content.addView(botsHeader)
        content.addView(space(10))

        if (importedBots.isEmpty()) {
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
                label("Import a bot ZIP to get started.", 12f, muted)
            )

            content.addView(empty)
        } else {
            importedBots.forEach { bot ->

                val botPanel = card(panel).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(15), dp(14), dp(15), dp(14))
                }

                botPanel.addView(label(bot.name, 16f, white, true))
                botPanel.addView(space(5))
                botPanel.addView(
                    label("${bot.runtime} • ${bot.entryFile}", 11f, muted)
                )
                botPanel.addView(space(7))

                val status = label("STATUS: CHECKING", 10f, muted, true)
                statusViews[bot.id] = status
                botPanel.addView(status)

                botPanel.addView(space(12))

                val botButtons = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val startBot = actionButton("START", cyan)
                startBot.setOnClickListener {
                    try {
                        if (bot.runtime != "Python") {
                            Toast.makeText(
                                this,
                                "This runtime is not supported yet.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            if (TokenStore.getToken(this@MainActivity, bot.id).isNullOrBlank()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Set this bot's Discord token first.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@setOnClickListener
                            }

                            startServiceCommand(
                                BotHostService.ACTION_START_BOT,
                                bot.id
                            )
                            status.text = "STATUS: START REQUESTED"
                            status.setTextColor(purple)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Start failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                val stopBot = actionButton("STOP", purple)
                stopBot.setOnClickListener {
                    try {
                        startServiceCommand(
                            BotHostService.ACTION_STOP_BOT,
                            bot.id
                        )
                        status.text = "STATUS: STOP REQUESTED"
                        status.setTextColor(purple)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Stop failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                val delete = actionButton("DELETE", red)
                delete.setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete ${bot.name}?")
                        .setMessage(
                            "BotHost will stop this bot before deleting its files and token."
                        )
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("DELETE") { _, _ ->
                            delete.isEnabled = false
                            delete.text = "STOPPING..."

                            try {
                                startServiceCommand(
                                    BotHostService.ACTION_STOP_BOT,
                                    bot.id
                                )

                                Thread {
                                    try {
                                        var stopped = false
                                        val deadline = System.currentTimeMillis() + 25000

                                        while (System.currentTimeMillis() < deadline) {
                                            Thread.sleep(500)

                                            val currentStatus =
                                                BotRuntime.getStatus(this@MainActivity, bot.id)

                                            if (currentStatus == "stopped") {
                                                stopped = true
                                                break
                                            }
                                        }

                                        if (!stopped) {
                                            throw IllegalStateException(
                                                "Bot did not confirm shutdown. Nothing was deleted."
                                            )
                                        }

                                        TokenStore.deleteToken(this@MainActivity, bot.id)
                                        BotManager.deleteBot(this@MainActivity, bot.id)

                                        handler.post {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Bot stopped and deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            buildDashboard()
                                        }
                                    } catch (e: Exception) {
                                        handler.post {
                                            delete.isEnabled = true
                                            delete.text = "DELETE"
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Delete failed: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }.start()
                            } catch (e: Exception) {
                                delete.isEnabled = true
                                delete.text = "DELETE"
                                Toast.makeText(
                                    this@MainActivity,
                                    "Stop request failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .show()
                }

                botButtons.addView(
                    startBot,
                    LinearLayout.LayoutParams(0, dp(44), 1f)
                )
                botButtons.addView(spaceHorizontal(6))
                botButtons.addView(
                    stopBot,
                    LinearLayout.LayoutParams(0, dp(44), 1f)
                )
                botButtons.addView(spaceHorizontal(6))
                botButtons.addView(
                    delete,
                    LinearLayout.LayoutParams(0, dp(44), 1f)
                )

                botPanel.addView(botButtons)

                val tokenButton = actionButton("SET TOKEN", cyan)
                tokenButton.setOnClickListener {
                    val tokenInput = EditText(this@MainActivity).apply {
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_VARIATION_PASSWORD
                        hint = "Paste this bot's Discord token"
                        setSingleLine(true)
                        setText(TokenStore.getToken(this@MainActivity, bot.id) ?: "")
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Discord token • ${bot.name}")
                        .setMessage("This token is encrypted and stored on this device.")
                        .setView(tokenInput)
                        .setNegativeButton("CANCEL", null)
                        .setNeutralButton("CLEAR") { _, _ ->
                            TokenStore.deleteToken(this@MainActivity, bot.id)
                            Toast.makeText(
                                this@MainActivity,
                                "Token cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setPositiveButton("SAVE") { _, _ ->
                            val value = tokenInput.text.toString().trim()
                            if (value.isNotEmpty()) {
                                try {
                                    TokenStore.saveToken(
                                        this@MainActivity,
                                        bot.id,
                                        value
                                    )
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Token saved securely",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Token save failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Enter a token first",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .show()
                }

                content.addView(tokenButton)
                content.addView(space(8))
                content.addView(botPanel)
                content.addView(space(10))
            }
        }

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
        console.addView(
            label("> BotHost dashboard initialized", 11f, white)
        )
        console.addView(
            label("> Python runtime: configured", 11f, muted)
        )
        console.addView(
            label("> Node.js runtime: not installed", 11f, muted)
        )
        console.addView(
            label("> Status monitoring: active", 11f, green)
        )

        content.addView(console)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        // Bottom navigation
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setBackgroundColor(Color.rgb(12, 19, 34))
        }

        listOf(
            "⌂\nHOME",
            "▤\nBOTS",
            ">_\nCONSOLE",
            "⚙\nSETTINGS"
        ).forEachIndexed { index, name ->

            val item = label(
                name,
                11f,
                if (index == 0) cyan else muted,
                index == 0
            )

            item.gravity = Gravity.CENTER
            item.setPadding(dp(8), dp(4), dp(8), dp(4))

            nav.addView(
                item,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            item.setOnClickListener {
                val section = listOf(
                    "Home", "Bots", "Console", "Settings"
                )[index]

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

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(37, 52, 77))
        }
    }

    private fun actionButton(
        title: String,
        color: Int
    ): Button {
        return Button(this).apply {
            text = title
            textSize = 10f
            setTextColor(bg)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = rounded(color, 14)
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(3), 0, dp(3), 0)
        }
    }

    private fun statCard(
        number: String,
        caption: String,
        color: Int
    ): LinearLayout {
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

    override fun onDestroy() {
        statusExecutor.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
