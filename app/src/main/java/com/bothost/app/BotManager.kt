package com.bothost.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
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
