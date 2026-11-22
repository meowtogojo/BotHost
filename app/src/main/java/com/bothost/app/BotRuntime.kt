package com.bothost.app

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

object BotRuntime {

    @Synchronized
    fun initialize(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    private fun runner(context: Context) =
        Python.getInstance().getModule("runner")

    fun startBot(
        context: Context,
        bot: BotInfo,
        token: String
    ): String {
        initialize(context)

        if (bot.runtime != "Python") {
            throw IllegalArgumentException(
                "Unsupported runtime: ${bot.runtime}"
            )
        }

        val root = File(bot.folder).canonicalFile
        val script = File(root, bot.entryFile).canonicalFile

        if (!script.path.startsWith(root.path + File.separator)) {
            throw SecurityException("Invalid bot entry path")
        }

        if (!script.isFile) {
            throw IllegalStateException("Bot entry file not found")
        }

        return runner(context)
            .callAttr(
                "run_script",
                script.absolutePath,
                bot.id,
                token
            )
            .toString()
    }

    fun stopBot(context: Context, botId: String): String {
        initialize(context)
        return runner(context).callAttr("stop_bot", botId).toString()
    }

    fun getStatus(context: Context, botId: String): String {
        if (!Python.isStarted()) return "stopped"
        return runner(context).callAttr("get_status", botId).toString()
    }

    fun getLogs(context: Context, botId: String): String {
        if (!Python.isStarted()) return ""
        return runner(context).callAttr("get_logs", botId).toString()
    }

    fun getError(context: Context, botId: String): String? {
        if (!Python.isStarted()) return null
        return runner(context).callAttr("get_error", botId)?.toString()
    }
}
