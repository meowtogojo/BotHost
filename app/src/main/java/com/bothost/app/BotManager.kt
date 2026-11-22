package com.bothost.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class BotInfo(
    val id: String,
    val name: String,
    val runtime: String,
    val entryFile: String,
    val folder: String
)

object BotManager {

    private const val PREFS = "bothost_bots"
    private const val MAX_SIZE = 100L * 1024 * 1024
    private const val MAX_ENTRIES = 5000

    fun getBots(context: Context): List<BotInfo> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return prefs.all.values.mapNotNull { value ->
            try {
                val json = JSONObject(value as String)
                BotInfo(
                    json.getString("id"),
                    json.getString("name"),
                    json.getString("runtime"),
                    json.getString("entryFile"),
                    json.getString("folder")
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun importZip(context: Context, uri: Uri): BotInfo {
        val id = UUID.randomUUID().toString()
        val root = File(context.filesDir, "bots/$id")

        if (!root.mkdirs()) {
            throw Exception("Could not create bot folder")
        }

        try {
            var totalSize = 0L
            var entries = 0

            val input = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Could not open selected ZIP")

            input.use { stream ->
                ZipInputStream(stream).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries++

                        if (entries > MAX_ENTRIES) {
                            throw Exception("ZIP contains too many files")
                        }

                        val name = entry.name.replace('\\', '/')
                        val parts = name.split("/")

                        if (
                            name.startsWith("/") ||
                            parts.any { it == ".." } ||
                            name.contains(":")
                        ) {
                            throw Exception("Unsafe ZIP file path")
                        }

                        val destination = File(root, name)
                        val canonicalRoot = root.canonicalPath + File.separator
                        val canonicalDestination = destination.canonicalPath

                        if (
                            canonicalDestination != root.canonicalPath &&
                            !canonicalDestination.startsWith(canonicalRoot)
                        ) {
                            throw Exception("Unsafe ZIP destination")
                        }

                        if (entry.isDirectory) {
                            destination.mkdirs()
                        } else {
                            destination.parentFile?.mkdirs()

                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(8192)

                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count == -1) break

                                    totalSize += count

                                    if (totalSize > MAX_SIZE) {
                                        throw Exception("ZIP exceeds 100 MB extracted size limit")
                                    }

                                    output.write(buffer, 0, count)
                                }
                            }
                        }

                        zip.closeEntry()
                    }
                }
            }

            val files = root.walkTopDown()
                .filter { it.isFile }
                .toList()

            val pythonCandidates = listOf("main.py", "bot.py", "app.py")
            val jsCandidates = listOf("index.js", "bot.js", "main.js")

            val pythonFile = pythonCandidates
                .firstNotNullOfOrNull { candidate ->
                    files.firstOrNull { it.name.equals(candidate, true) }
                }

            val jsFile = jsCandidates
                .firstNotNullOfOrNull { candidate ->
                    files.firstOrNull { it.name.equals(candidate, true) }
                }

            val packageJson = files.firstOrNull {
                it.name.equals("package.json", true)
            }

            val runtime: String
            val entry: File

            when {
                pythonFile != null -> {
                    runtime = "Python"
                    entry = pythonFile
                }

                jsFile != null -> {
                    runtime = "JavaScript"
                    entry = jsFile
                }

                packageJson != null -> {
                    throw Exception(
                        "Found package.json, but no supported JavaScript entry file"
                    )
                }

                else -> {
                    throw Exception(
                        "No supported entry file found. Expected main.py, bot.py, index.js or similar."
                    )
                }
            }

            val displayName = getDisplayName(context, uri)
                .substringBeforeLast(".")
                .ifBlank { "My Bot" }

            val relativeEntry = entry.relativeTo(root).invariantSeparatorsPath

            val bot = BotInfo(
                id = id,
                name = displayName,
                runtime = runtime,
                entryFile = relativeEntry,
                folder = root.absolutePath
            )

            val json = JSONObject().apply {
                put("id", bot.id)
                put("name", bot.name)
                put("runtime", bot.runtime)
                put("entryFile", bot.entryFile)
                put("folder", bot.folder)
            }

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(id, json.toString())
                .apply()

            return bot

        } catch (e: Exception) {
            root.deleteRecursively()
            throw e
        }
    }


    fun createProject(context: Context, projectName: String): BotInfo {
        val name = projectName.trim().take(40)
        require(name.isNotBlank()) { "Enter an application name" }

        val id = UUID.randomUUID().toString()
        val root = File(context.filesDir, "bots/$id")

        if (!root.mkdirs()) {
            throw Exception("Could not create application folder")
        }

        val starterCode = """
import os
import discord
from discord.ext import commands

intents = discord.Intents.default()
bot = commands.Bot(command_prefix="!", intents=intents)

@bot.event
async def on_ready():
    await bot.tree.sync()
    print(f"Logged in as {bot.user}")

@bot.tree.command(name="ping", description="Check if the bot is online")
async def ping(interaction: discord.Interaction):
    await interaction.response.send_message("Pong!")

bot.run(os.getenv("DISCORD_TOKEN"))
""".trimIndent()

        try {
            val entry = File(root, "main.py")
            entry.writeText(starterCode)

            val bot = BotInfo(
                id = id,
                name = name,
                runtime = "Python",
                entryFile = "main.py",
                folder = root.absolutePath
            )

            val json = JSONObject().apply {
                put("id", bot.id)
                put("name", bot.name)
                put("runtime", bot.runtime)
                put("entryFile", bot.entryFile)
                put("folder", bot.folder)
            }

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(id, json.toString())
                .apply()

            return bot
        } catch (e: Exception) {
            root.deleteRecursively()
            throw e
        }
    }


    private fun projectFile(bot: BotInfo, relativePath: String, allowRoot: Boolean = false): File {
        val root = File(bot.folder).canonicalFile
        val clean = relativePath.replace('\\', '/').trim('/')

        if (clean.isBlank()) {
            require(allowRoot) { "Project root is not a file" }
            return root
        }

        val parts = clean.split("/")
        require(parts.none { it.isBlank() || it == "." || it == ".." || it.contains(":") }) {
            "Invalid project path"
        }

        val target = File(root, clean).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) {
            "Project path is outside this application"
        }
        return target
    }

    fun listProjectFiles(
        bot: BotInfo,
        directory: String = ""
    ): List<String> {
        val current = projectFile(bot, directory, allowRoot = true)
        require(current.isDirectory) { "Folder does not exist" }

        return current.listFiles()
            ?.filter { it.name != "__pycache__" }
            ?.map { file ->
                val relative = file.relativeTo(File(bot.folder).canonicalFile)
                    .invariantSeparatorsPath
                if (file.isDirectory) "$relative/" else relative
            }
            ?.sortedWith(compareBy<String>({ !it.endsWith("/") }, { it.lowercase() }))
            ?: emptyList()
    }

    fun createProjectFile(bot: BotInfo, relativePath: String, directory: Boolean) {
        val target = projectFile(bot, relativePath)
        require(!target.exists()) { "A file or folder with that name already exists" }
        target.parentFile?.let {
            require(it.isDirectory) { "Parent folder does not exist" }
        }

        val created = if (directory) target.mkdirs() else {
            target.parentFile?.mkdirs()
            target.createNewFile()
        }
        require(created) { "Could not create item" }
    }

    fun readProjectFile(bot: BotInfo, relativePath: String): String {
        val target = projectFile(bot, relativePath)
        require(target.isFile) { "File does not exist" }
        require(target.length() <= 1024 * 1024) { "File is larger than 1 MB" }
        return target.readText()
    }

    fun writeProjectFile(bot: BotInfo, relativePath: String, content: String) {
        val target = projectFile(bot, relativePath)
        require(target.isFile) { "File does not exist" }
        require(content.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) {
            "File content is larger than 1 MB"
        }
        target.writeText(content)
    }

    fun renameProjectItem(bot: BotInfo, relativePath: String, newName: String) {
        val source = projectFile(bot, relativePath)
        val name = newName.trim()
        require(source.exists()) { "Item does not exist" }
        require(name.isNotBlank() && name != "." && name != "..") { "Enter a valid name" }
        require(!name.contains("/") && !name.contains("\\") && !name.contains(":")) {
            "Use a name without folder separators"
        }

        val entry = projectFile(bot, bot.entryFile)
        require(
            source.canonicalFile != entry.canonicalFile &&
            !entry.canonicalPath.startsWith(source.canonicalPath + File.separator)
        ) {
            "Cannot rename the bot's main entry file or its parent folder"
        }

        val destination = File(source.parentFile, name).canonicalFile
        val root = File(bot.folder).canonicalFile
        require(destination.path.startsWith(root.path + File.separator)) {
            "Invalid destination"
        }
        require(!destination.exists()) { "That name is already in use" }
        require(source.renameTo(destination)) { "Could not rename item" }
    }

    fun deleteProjectItem(bot: BotInfo, relativePath: String) {
        val target = projectFile(bot, relativePath)
        require(target.exists()) { "Item does not exist" }

        val entry = projectFile(bot, bot.entryFile).canonicalFile
        require(target.canonicalFile != entry && !entry.path.startsWith(target.canonicalPath + File.separator)) {
            "Cannot delete the bot's main entry file or its parent folder"
        }
        require(target.deleteRecursively()) { "Could not delete item" }
    }


    private fun slashCommandsFile(bot: BotInfo): File {
        val root = File(bot.folder).canonicalFile
        val file = File(root, "slash_commands.json").canonicalFile
        require(file.path.startsWith(root.path + File.separator))
        return file
    }

    fun getSlashCommands(bot: BotInfo): JSONArray {
        val file = slashCommandsFile(bot)
        return if (file.isFile) JSONArray(file.readText()) else JSONArray()
    }

    fun saveSlashCommand(bot: BotInfo, name: String, description: String, code: String) {
        val cleanName = name.trim()
        val cleanDescription = description.trim()

        require(Regex("[a-z0-9_-]{1,32}").matches(cleanName)) {
            "Use 1–32 lowercase letters, numbers, hyphens or underscores"
        }
        require(cleanDescription.isNotBlank() && cleanDescription.length <= 100) {
            "Description must be 1–100 characters"
        }
        require(code.isNotBlank()) { "Command code cannot be empty" }

        val root = File(bot.folder).canonicalFile
        val mainFile = File(root, bot.entryFile).canonicalFile
        require(mainFile.path.startsWith(root.path + File.separator) && mainFile.isFile) {
            "Bot entry file is missing"
        }

        val mainText = mainFile.readText()

        require(cleanName != "ping") {
            "The starter bot already includes /ping. Choose another command name."
        }

        require(
            mainText.contains("BOT_HOST_SLASH_COMMANDS_BEGIN") ||
            Regex("(?m)^.*\\bbot\\s*=\\s*commands\\.Bot\\s*\\(.*$").containsMatchIn(mainText)
        ) {
            "Your main.py needs a commands.Bot(...) declaration"
        }

        val old = getSlashCommands(bot)
        val updated = JSONArray()
        var replaced = false

        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            if (item.optString("name") == cleanName) {
                updated.put(
                    JSONObject()
                        .put("name", cleanName)
                        .put("description", cleanDescription)
                        .put("code", code)
                )
                replaced = true
            } else {
                updated.put(item)
            }
        }

        if (!replaced) {
            updated.put(
                JSONObject()
                    .put("name", cleanName)
                    .put("description", cleanDescription)
                    .put("code", code)
            )
        }

        slashCommandsFile(bot).writeText(updated.toString(2))
        regenerateSlashModule(bot, updated)
    }

    fun deleteSlashCommand(bot: BotInfo, name: String) {
        val old = getSlashCommands(bot)
        val updated = JSONArray()

        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            if (item.optString("name") != name) updated.put(item)
        }

        slashCommandsFile(bot).writeText(updated.toString(2))
        regenerateSlashModule(bot, updated)
    }

    private fun regenerateSlashModule(bot: BotInfo, commands: JSONArray) {
        val root = File(bot.folder).canonicalFile
        val mainFile = File(root, bot.entryFile).canonicalFile
        require(mainFile.path.startsWith(root.path + File.separator) && mainFile.isFile)

        val python = StringBuilder()
        python.append("import discord\n\n")
        python.append("def register_slash_commands(bot):\n")

        if (commands.length() == 0) {
            python.append("    pass\n")
        } else {
            for (i in 0 until commands.length()) {
                val item = commands.getJSONObject(i)
                python.append(
                    "    @bot.tree.command(name=${JSONObject.quote(item.getString("name"))}, " +
                    "description=${JSONObject.quote(item.getString("description"))})\n"
                )
                python.append(
                    "    async def bot_host_command_$i(interaction: discord.Interaction):\n"
                )

                val lines = item.getString("code")
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .lines()

                for (line in lines) {
                    python.append(if (line.isBlank()) "\n" else "        $line\n")
                }
                python.append("\n")
            }
        }

        File(root, "slash_commands.py").writeText(python.toString())

        val begin = "# BOT_HOST_SLASH_COMMANDS_BEGIN"
        val end = "# BOT_HOST_SLASH_COMMANDS_END"
        val block = "$begin\nfrom slash_commands import register_slash_commands\nregister_slash_commands(bot)\n$end"

        var mainText = mainFile.readText()
        val start = mainText.indexOf(begin)
        val finish = mainText.indexOf(end)

        if (start >= 0 && finish >= start) {
            mainText = mainText.substring(0, start) + block +
                mainText.substring(finish + end.length)
        } else {
            val declaration = Regex(
                "(?m)^.*\\bbot\\s*=\\s*commands\\.Bot\\s*\\(.*$"
            ).find(mainText) ?: throw IllegalStateException(
                "Could not find bot = commands.Bot(...)"
            )

            val insertAt = declaration.range.last + 1
            mainText = mainText.substring(0, insertAt) +
                "\n\n" + block + mainText.substring(insertAt)
        }

        mainFile.writeText(mainText)
    }

    fun deleteBot(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bot = getBots(context).firstOrNull { it.id == id }

        bot?.let {
            File(it.folder).deleteRecursively()
        }

        prefs.edit().remove(id).apply()
    }

    private fun getDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: "My Bot.zip"
                } else {
                    "My Bot.zip"
                }
            } ?: "My Bot.zip"
        } catch (_: Exception) {
            "My Bot.zip"
        }
    }
}
