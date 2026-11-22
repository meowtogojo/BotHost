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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val bg = Color.rgb(8, 12, 25)
    private val panel = Color.rgb(17, 26, 46)
    private val panel2 = Color.rgb(23, 35, 59)
    private val cyan = Color.rgb(62, 213, 255)
    private val purple = Color.rgb(160, 112, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(145, 162, 190)
    private val green = Color.rgb(75, 220, 145)
    private val red = Color.rgb(255, 100, 110)

    private lateinit var runningCount: TextView
    private lateinit var importedCount: TextView

    private val statusViews = mutableMapOf<String, TextView>()
    private lateinit var pageContent: LinearLayout
    private var currentPage = "Home"
    private var selectedWorkspaceBot: BotInfo? = null
    private var workspaceSection = "Overview"

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            setBackgroundColor(bg)
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brand.addView(label("B O T H O S T", 21f, cyan, true))
        brand.addView(label("ANDROID BOT CONTROL CENTER", 10f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val version = label("ALPHA 0.2", 10f, purple, true)
        version.setPadding(dp(12), dp(8), dp(12), dp(8))
        version.background = rounded(panel2, 30)
        header.addView(version)
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        pageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(24))
        }

        scroll.addView(pageContent)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(10), dp(5), dp(10))
            setBackgroundColor(Color.rgb(12, 19, 34))
        }

        listOf("Home", "Bots", "Console", "Settings").forEach { page ->
            val item = label(
                when (page) {
                    "Home" -> "⌂\nHOME"
                    "Bots" -> "▤\nBOTS"
                    "Console" -> ">_\nCONSOLE"
                    else -> "⚙\nSETTINGS"
                },
                10f,
                if (currentPage == page) cyan else muted,
                currentPage == page
            )
            item.gravity = Gravity.CENTER
            item.setPadding(dp(5), dp(5), dp(5), dp(5))
            item.setOnClickListener {
                currentPage = page
                selectedWorkspaceBot = null
                buildDashboard()
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }

        root.addView(nav)
        setContentView(root)

        runningCount = TextView(this)
        importedCount = TextView(this)
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        pageContent.removeAllViews()
        statusViews.clear()

        val bot = selectedWorkspaceBot
        if (bot != null) {
            renderWorkspace(bot)
            return
        }

        when (currentPage) {
            "Home" -> renderHome()
            "Bots" -> renderBots()
            "Console" -> renderConsole()
            "Settings" -> renderSettings()
        }
    }

    private fun sectionTitle(value: String) {
        pageContent.addView(label(value, 13f, muted, true))
        pageContent.addView(space(10))
    }

    private fun renderHome() {
        val bots = BotManager.getBots(this)

        val hero = card(panel2).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }
        hero.addView(label("YOUR BOT SERVER", 11f, muted, true))
        hero.addView(space(8))
        hero.addView(label("Welcome to BotHost.", 23f, white, true))
        hero.addView(space(6))
        hero.addView(label("Create, manage and host your Discord bots.", 13f, muted))
        pageContent.addView(hero)
        pageContent.addView(space(20))

        sectionTitle("OVERVIEW")

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val running = statCard("0", "RUNNING BOTS", cyan)
        val total = statCard(bots.size.toString(), "APPLICATIONS", purple)
        runningCount = running.getChildAt(0) as TextView
        importedCount = total.getChildAt(0) as TextView

        stats.addView(running, LinearLayout.LayoutParams(0, dp(105), 1f))
        stats.addView(spaceHorizontal(10))
        stats.addView(total, LinearLayout.LayoutParams(0, dp(105), 1f))
        pageContent.addView(stats)
        pageContent.addView(space(22))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("MY APPLICATIONS", 13f, muted, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val add = actionButton("+ NEW", cyan)
        add.setOnClickListener { createApplication() }
        heading.addView(add)
        pageContent.addView(heading)
        pageContent.addView(space(10))

        if (bots.isEmpty()) {
            pageContent.addView(label("No applications yet. Create your first bot in the Bots tab.", 13f, muted))
        } else {
            bots.take(3).forEach { bot ->
                val item = card(panel).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                }
                item.addView(label(bot.name, 16f, white, true))
                item.addView(space(4))
                item.addView(label("${bot.runtime} • ${bot.entryFile}", 11f, muted))
                item.addView(space(8))
                val open = actionButton("OPEN WORKSPACE", cyan)
                open.setOnClickListener { openWorkspace(bot) }
                item.addView(open)
                pageContent.addView(item)
                pageContent.addView(space(9))
            }
        }
    }

    private fun renderBots() {
        val bots = BotManager.getBots(this)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("MY APPLICATIONS", 17f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val add = actionButton("+ NEW APPLICATION", cyan)
        add.setOnClickListener { createApplication() }
        heading.addView(add)
        pageContent.addView(heading)
        pageContent.addView(space(15))

        if (bots.isEmpty()) {
            val empty = card(panel).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(25), dp(18), dp(25))
            }
            empty.addView(label("◈", 34f, cyan, true))
            empty.addView(space(8))
            empty.addView(label("No applications created yet", 16f, white, true))
            empty.addView(space(5))
            empty.addView(label("Create an application to open your bot workspace.", 12f, muted))
            pageContent.addView(empty)
            return
        }

        bots.forEach { bot ->
            val item = card(panel).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(14), dp(15), dp(14))
            }
            item.addView(label(bot.name, 17f, white, true))
            item.addView(space(4))
            item.addView(label("${bot.runtime} • ${bot.entryFile}", 11f, muted))
            item.addView(space(7))

            val status = label("STATUS: CHECKING", 10f, muted, true)
            statusViews[bot.id] = status
            item.addView(status)
            item.addView(space(12))

            val open = actionButton("OPEN WORKSPACE", cyan)
            open.setOnClickListener { openWorkspace(bot) }
            item.addView(open)
            item.addView(space(8))

            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val start = actionButton("START", cyan)
            start.setOnClickListener {
                if (bot.runtime != "Python") {
                    Toast.makeText(this, "This runtime is not supported yet.", Toast.LENGTH_LONG).show()
                } else if (TokenStore.getToken(this, bot.id).isNullOrBlank()) {
                    Toast.makeText(this, "Set this bot's Discord token first.", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        startServiceCommand(BotHostService.ACTION_START_BOT, bot.id)
                        status.text = "STATUS: START REQUESTED"
                        status.setTextColor(purple)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Start failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            val stop = actionButton("STOP", purple)
            stop.setOnClickListener {
                try {
                    startServiceCommand(BotHostService.ACTION_STOP_BOT, bot.id)
                    status.text = "STATUS: STOP REQUESTED"
                    status.setTextColor(purple)
                } catch (e: Exception) {
                    Toast.makeText(this, "Stop failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            val delete = actionButton("DELETE", red)
            delete.setOnClickListener { confirmDeleteBot(bot, delete) }

            controls.addView(start, LinearLayout.LayoutParams(0, dp(44), 1f))
            controls.addView(spaceHorizontal(6))
            controls.addView(stop, LinearLayout.LayoutParams(0, dp(44), 1f))
            controls.addView(spaceHorizontal(6))
            controls.addView(delete, LinearLayout.LayoutParams(0, dp(44), 1f))
            item.addView(controls)

            pageContent.addView(item)
            pageContent.addView(space(10))
        }
    }

    private fun confirmDeleteBot(bot: BotInfo, button: Button) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${bot.name}?")
            .setMessage("BotHost will stop this bot before deleting its files and token.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                button.isEnabled = false
                button.text = "STOPPING..."
                try {
                    startServiceCommand(BotHostService.ACTION_STOP_BOT, bot.id)
                    Thread {
                        try {
                            var stopped = false
                            val deadline = System.currentTimeMillis() + 25000
                            while (System.currentTimeMillis() < deadline) {
                                Thread.sleep(500)
                                if (BotRuntime.getStatus(this@MainActivity, bot.id) == "stopped") {
                                    stopped = true
                                    break
                                }
                            }
                            if (!stopped) {
                                throw IllegalStateException("Bot did not confirm shutdown. Nothing was deleted.")
                            }
                            TokenStore.deleteToken(this@MainActivity, bot.id)
                            BotManager.deleteBot(this@MainActivity, bot.id)
                            handler.post {
                                Toast.makeText(this@MainActivity, "Bot stopped and deleted", Toast.LENGTH_SHORT).show()
                                selectedWorkspaceBot = null
                                buildDashboard()
                            }
                        } catch (e: Exception) {
                            handler.post {
                                button.isEnabled = true
                                button.text = "DELETE"
                                Toast.makeText(this@MainActivity, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                } catch (e: Exception) {
                    button.isEnabled = true
                    button.text = "DELETE"
                    Toast.makeText(this, "Stop request failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun openWorkspace(bot: BotInfo) {
        selectedWorkspaceBot = bot
        workspaceSection = "Overview"
        renderCurrentPage()
    }

    private fun renderWorkspace(bot: BotInfo) {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = actionButton("‹ BOTS", panel2)
        back.setOnClickListener {
            selectedWorkspaceBot = null
            currentPage = "Bots"
            buildDashboard()
        }
        top.addView(back)
        top.addView(spaceHorizontal(10))
        top.addView(label(bot.name, 19f, white, true))
        pageContent.addView(top)
        pageContent.addView(space(8))
        pageContent.addView(label("${bot.runtime} application • ${bot.entryFile}", 11f, muted))
        pageContent.addView(space(16))

        val sections = listOf("Overview", "Code", "Files", "Slash Commands", "Settings")
        val sectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        sections.forEach { section ->
            val button = actionButton(
                section,
                if (workspaceSection == section) cyan else panel2
            )
            button.textSize = 10f
            button.setOnClickListener {
                workspaceSection = section
                renderCurrentPage()
            }
            sectionRow.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        val horizontal = ScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(sectionRow)
        }
        pageContent.addView(horizontal)
        pageContent.addView(space(18))

        when (workspaceSection) {
            "Overview" -> {
                sectionTitle("BOT OVERVIEW")
                val info = card(panel).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(15), dp(15), dp(15), dp(15))
                }
                info.addView(label(bot.name, 18f, white, true))
                info.addView(space(6))
                info.addView(label("Runtime: ${bot.runtime}", 12f, muted))
                info.addView(label("Entry file: ${bot.entryFile}", 12f, muted))
                pageContent.addView(info)
                pageContent.addView(space(12))

                val start = actionButton("START BOT", cyan)
                start.setOnClickListener {
                    if (TokenStore.getToken(this, bot.id).isNullOrBlank()) {
                        Toast.makeText(this, "Set this bot's token in Settings first.", Toast.LENGTH_LONG).show()
                    } else {
                        startServiceCommand(BotHostService.ACTION_START_BOT, bot.id)
                    }
                }
                pageContent.addView(start)
                pageContent.addView(space(8))
                val stop = actionButton("STOP BOT", purple)
                stop.setOnClickListener { startServiceCommand(BotHostService.ACTION_STOP_BOT, bot.id) }
                pageContent.addView(stop)
            }
            "Code" -> {
                sectionTitle("CODE EDITOR")
                pageContent.addView(label("Edit your bot's main Python file.", 13f, muted))
                pageContent.addView(space(12))
                val edit = actionButton("OPEN CODE EDITOR", purple)
                edit.setOnClickListener { openCodeEditor(bot) }
                pageContent.addView(edit)
            }
            "Files" -> {
                sectionTitle("FILE MANAGER")
                pageContent.addView(label("Create, edit, rename and manage project files.", 13f, muted))
                pageContent.addView(space(12))
                val files = actionButton("OPEN FILE MANAGER", cyan)
                files.setOnClickListener { openFileManager(bot) }
                pageContent.addView(files)
            }
            "Slash Commands" -> {
                sectionTitle("SLASH COMMAND BUILDER")
                pageContent.addView(label("Create and manage your Discord slash commands.", 13f, muted))
                pageContent.addView(space(12))
                val commands = actionButton("OPEN SLASH COMMANDS", purple)
                commands.setOnClickListener { openSlashCommands(bot) }
                pageContent.addView(commands)
            }
            "Settings" -> {
                sectionTitle("BOT SETTINGS")
                val token = actionButton("MANAGE DISCORD TOKEN", cyan)
                token.setOnClickListener { showTokenDialog(bot) }
                pageContent.addView(token)
                pageContent.addView(space(10))
                val delete = actionButton("DELETE APPLICATION", red)
                delete.setOnClickListener { confirmDeleteBot(bot, delete) }
                pageContent.addView(delete)
            }
        }
    }

    private fun showTokenDialog(bot: BotInfo) {
        val tokenInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Paste this bot's Discord token"
            setSingleLine(true)
            setText(TokenStore.getToken(this@MainActivity, bot.id) ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Discord token • ${bot.name}")
            .setMessage("This token is encrypted and stored on this device.")
            .setView(tokenInput)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("CLEAR") { _, _ ->
                TokenStore.deleteToken(this@MainActivity, bot.id)
                Toast.makeText(this, "Token cleared", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("SAVE") { _, _ ->
                val value = tokenInput.text.toString().trim()
                if (value.isNotEmpty()) {
                    try {
                        TokenStore.saveToken(this@MainActivity, bot.id, value)
                        Toast.makeText(this, "Token saved securely", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Token save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Enter a token first", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun renderConsole() {
        sectionTitle("BOT CONSOLE")
        val bots = BotManager.getBots(this)
        if (bots.isEmpty()) {
            pageContent.addView(label("No bot applications yet.", 13f, muted))
            return
        }
        bots.forEach { bot ->
            val panelView = card(Color.rgb(7, 11, 20)).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            panelView.addView(label(bot.name, 14f, cyan, true))
            panelView.addView(space(8))
            val logs = try {
                BotRuntime.getLogs(this, bot.id)
            } catch (_: Exception) {
                "No logs available."
            }
            panelView.addView(label(logs.ifBlank { "No output yet." }, 11f, white))
            pageContent.addView(panelView)
            pageContent.addView(space(10))
        }
    }

    private fun renderSettings() {
        sectionTitle("SETTINGS")
        val info = card(panel).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        info.addView(label("BOT HOST", 17f, white, true))
        info.addView(space(6))
        info.addView(label("Android bot hosting control center", 12f, muted))
        info.addView(space(8))
        info.addView(label("Your bot tokens remain encrypted using the existing secure storage system.", 12f, muted))
        pageContent.addView(info)
    }

    private fun createApplication() {
        val nameInput = EditText(this).apply {
            hint = "Application name"
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("New Application")
            .setMessage("Create a new Discord bot project.")
            .setView(nameInput)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                try {
                    val bot = BotManager.createProject(
                        this,
                        nameInput.text.toString()
                    )

                    Toast.makeText(
                        this,
                        "Application created",
                        Toast.LENGTH_SHORT
                    ).show()

                    currentPage = "Bots"
                    buildDashboard()
                    openWorkspace(bot)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Creation failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun openCodeEditor(bot: BotInfo) {
        val root = java.io.File(bot.folder).canonicalFile
        val file = java.io.File(root, bot.entryFile).canonicalFile

        if (!file.path.startsWith(root.path + java.io.File.separator)) {
            Toast.makeText(this, "Invalid project file", Toast.LENGTH_LONG).show()
            return
        }

        val editor = EditText(this).apply {
            setText(file.readText())
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 18
            setHorizontallyScrolling(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }

        val scroll = ScrollView(this).apply {
            addView(editor)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        AlertDialog.Builder(this)
            .setTitle("${bot.name} • main.py")
            .setView(scroll)
            .setNegativeButton("CLOSE", null)
            .setPositiveButton("SAVE") { _, _ ->
                try {
                    file.writeText(editor.text.toString())
                    Toast.makeText(
                        this,
                        "Code saved",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Save failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }


    private fun openFileManager(bot: BotInfo) {
        var currentDirectory = ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val navigationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val locationLabel = label("PROJECT ROOT", 12f, white, true)
        navigationRow.addView(
            locationLabel,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val upButton = actionButton("UP", purple)
        navigationRow.addView(upButton)
        layout.addView(navigationRow)
        layout.addView(space(6))

        val fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(fileList)
        }

        layout.addView(scroll, LinearLayout.LayoutParams(-1, dp(300)))

        fun refreshFiles() {
            fileList.removeAllViews()

            locationLabel.text = if (currentDirectory.isBlank()) {
                "PROJECT ROOT"
            } else {
                currentDirectory
            }

            upButton.visibility =
                if (currentDirectory.isBlank()) View.GONE else View.VISIBLE

            try {
                val files = BotManager.listProjectFiles(bot, currentDirectory)

                if (files.isEmpty()) {
                    fileList.addView(
                        label("This folder is empty.", 13f, muted)
                    )
                }

                files.forEach { path ->
                    val folder = path.endsWith("/")
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(3), dp(5), dp(3), dp(5))
                    }

                    val displayName = path.removeSuffix("/")
                        .substringAfterLast("/")

                    val itemLabel = label(
                        (if (folder) "FOLDER  " else "FILE  ") + displayName,
                        11f,
                        white
                    )

                    row.addView(
                        itemLabel,
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )

                    if (folder) {
                        itemLabel.setOnClickListener {
                            currentDirectory = path.removeSuffix("/")
                            refreshFiles()
                        }

                        val open = actionButton("OPEN", cyan)
                        open.setOnClickListener {
                            currentDirectory = path.removeSuffix("/")
                            refreshFiles()
                        }
                        row.addView(open)
                    } else {
                        val open = actionButton("OPEN", cyan)
                        open.setOnClickListener {
                            openManagedFile(bot, path)
                        }
                        row.addView(open)
                    }

                    val rename = actionButton("RENAME", purple)
                    rename.setOnClickListener {
                        val input = EditText(this@MainActivity).apply {
                            setSingleLine(true)
                            setText(displayName)
                        }

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Rename item")
                            .setView(input)
                            .setNegativeButton("CANCEL", null)
                            .setPositiveButton("RENAME") { _, _ ->
                                try {
                                    BotManager.renameProjectItem(
                                        bot,
                                        path.removeSuffix("/"),
                                        input.text.toString()
                                    )
                                    refreshFiles()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        e.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .show()
                    }
                    row.addView(rename)

                    val delete = actionButton("DELETE", red)
                    delete.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete item?")
                            .setMessage("Delete $displayName?")
                            .setNegativeButton("CANCEL", null)
                            .setPositiveButton("DELETE") { _, _ ->
                                try {
                                    BotManager.deleteProjectItem(
                                        bot,
                                        path.removeSuffix("/")
                                    )
                                    refreshFiles()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        e.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .show()
                    }
                    row.addView(delete)

                    fileList.addView(row)
                }
            } catch (e: Exception) {
                fileList.addView(
                    label("File error: ${e.message}", 12f, red)
                )
            }
        }

        upButton.setOnClickListener {
            currentDirectory = currentDirectory
                .substringBeforeLast("/", "")
            refreshFiles()
        }

        fun createItem(directory: Boolean) {
            val input = EditText(this@MainActivity).apply {
                hint = if (directory) {
                    "Folder name"
                } else {
                    "File name (e.g. config.py)"
                }
                setSingleLine(true)
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle(if (directory) "Create folder" else "Create file")
                .setView(input)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CREATE") { _, _ ->
                    try {
                        val name = input.text.toString().trim()
                        val path = if (currentDirectory.isBlank()) {
                            name
                        } else {
                            "$currentDirectory/$name"
                        }

                        BotManager.createProjectFile(bot, path, directory)
                        refreshFiles()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            e.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .show()
        }

        val createRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val newFile = actionButton("+ FILE", cyan)
        newFile.setOnClickListener { createItem(false) }

        val newFolder = actionButton("+ FOLDER", purple)
        newFolder.setOnClickListener { createItem(true) }

        createRow.addView(
            newFile,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        createRow.addView(spaceHorizontal(8))
        createRow.addView(
            newFolder,
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )

        layout.addView(createRow)

        refreshFiles()

        AlertDialog.Builder(this)
            .setTitle("${bot.name} - FILE MANAGER")
            .setView(layout)
            .setPositiveButton("CLOSE", null)
            .show()
    }


    private fun openSlashCommands(bot: BotInfo) {
        try {
            val commands = BotManager.getSlashCommands(bot)
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }

            layout.addView(label("SLASH COMMAND BUILDER", 16f, Color.WHITE, true))
            layout.addView(space(5))
            layout.addView(label("Create commands that users can run with / in Discord.", 13f, Color.LTGRAY))
            layout.addView(space(12))

            if (commands.length() == 0) {
                layout.addView(label("No custom slash commands yet.", 14f, Color.LTGRAY))
                layout.addView(space(10))
            }

            for (i in 0 until commands.length()) {
                val item = commands.getJSONObject(i)
                val name = item.optString("name")
                val panel = card(Color.rgb(23, 32, 49)).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }

                panel.addView(label("/$name", 15f, Color.WHITE, true))
                panel.addView(space(3))
                panel.addView(label(item.optString("description"), 12f, Color.LTGRAY))
                panel.addView(space(8))

                val edit = actionButton("EDIT COMMAND", purple)
                edit.setOnClickListener { editSlashCommand(bot, item) }
                panel.addView(edit)
                panel.addView(space(5))

                val delete = actionButton("DELETE COMMAND", Color.rgb(160, 55, 65))
                delete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Delete /$name?")
                        .setMessage("Remove this command from the bot?")
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("DELETE") { _, _ ->
                            try {
                                BotManager.deleteSlashCommand(bot, name)
                                Toast.makeText(this, "Command deleted", Toast.LENGTH_SHORT).show()
                                openSlashCommands(bot)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        .show()
                }
                panel.addView(delete)
                layout.addView(panel)
                layout.addView(space(9))
            }

            val add = actionButton("+ NEW SLASH COMMAND", purple)
            add.setOnClickListener { editSlashCommand(bot, null) }
            layout.addView(add)

            val scroll = ScrollView(this).apply { addView(layout) }
            AlertDialog.Builder(this)
                .setTitle("${bot.name} • SLASH COMMANDS")
                .setView(scroll)
                .setPositiveButton("CLOSE", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open commands: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun editSlashCommand(bot: BotInfo, existing: JSONObject?) {
        val nameInput = EditText(this).apply {
            hint = "Command name (example: hello)"
            setSingleLine(true)
            setText(existing?.optString("name") ?: "")
        }

        val descriptionInput = EditText(this).apply {
            hint = "Description (example: Say hello)"
            setSingleLine(true)
            setText(existing?.optString("description") ?: "")
        }

        val codeInput = EditText(this).apply {
            hint = "Python code for this command"
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            minLines = 7
            setHorizontallyScrolling(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(existing?.optString("code") ?: """await interaction.response.send_message("Hello!")""")
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
            addView(label("COMMAND NAME", 12f, Color.LTGRAY, true))
            addView(nameInput)
            addView(space(5))
            addView(label("DESCRIPTION", 12f, Color.LTGRAY, true))
            addView(descriptionInput)
            addView(space(5))
            addView(label("PYTHON CODE", 12f, Color.LTGRAY, true))
            addView(codeInput)
        }

        val scroll = ScrollView(this).apply { addView(form) }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New Slash Command" else "Edit Slash Command")
            .setView(scroll)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                try {
                    BotManager.saveSlashCommand(
                        bot,
                        nameInput.text.toString(),
                        descriptionInput.text.toString(),
                        codeInput.text.toString()
                    )
                    Toast.makeText(this, "Slash command saved", Toast.LENGTH_SHORT).show()
                    openSlashCommands(bot)
                } catch (e: Exception) {
                    Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun openManagedFile(bot: BotInfo, path: String) {
        try {
            val editor = EditText(this).apply {
                setText(BotManager.readProjectFile(bot, path))
                typeface = Typeface.MONOSPACE
                textSize = 13f
                gravity = Gravity.TOP or Gravity.START
                minLines = 16
                setHorizontallyScrolling(true)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            val scroll = ScrollView(this).apply { addView(editor) }

            AlertDialog.Builder(this)
                .setTitle(path)
                .setView(scroll)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("SAVE") { _, _ ->
                    try {
                        BotManager.writeProjectFile(bot, path, editor.text.toString())
                        Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Open failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
