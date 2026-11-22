package com.bothost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors

class BotHostService : Service() {

    companion object {
        const val ACTION_START_BOT = "bothost.START_BOT"
        const val ACTION_STOP_BOT = "bothost.STOP_BOT"
        const val ACTION_STOP_HOST = "bothost.STOP_HOST"

        const val EXTRA_BOT_ID = "bot_id"

        private const val CHANNEL_ID = "bothost_runtime"
        private const val NOTIFICATION_ID = 4101
        private const val TAG = "BotHost"
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("BotHost is active")
                .setContentText("Managing your hosted bots")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("BotHost is active")
                .setContentText("Managing your hosted bots")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()
        }

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START_BOT -> {
                val botId = intent.getStringExtra(EXTRA_BOT_ID)

                if (botId != null) {
                    executor.execute {
                        try {
                            val bot = BotManager.getBots(this)
                                .firstOrNull { it.id == botId }
                                ?: throw IllegalArgumentException("Bot not found")

                            val token = TokenStore.getToken(this, botId)
                                ?: throw IllegalStateException(
                                    "No Discord token configured for this bot"
                                )

                            BotRuntime.startBot(this, bot, token)

                        } catch (e: Exception) {
                            Log.e(TAG, "Could not start bot $botId", e)
                        }
                    }
                }
            }

            ACTION_STOP_BOT -> {
                val botId = intent.getStringExtra(EXTRA_BOT_ID)

                if (botId != null) {
                    executor.execute {
                        try {
                            BotRuntime.stopBot(this, botId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not stop bot $botId", e)
                        }
                    }
                }
            }

            ACTION_STOP_HOST -> {
                executor.execute {
                    try {
                        BotManager.getBots(this).forEach { bot ->
                            if (bot.runtime == "Python") {
                                try {
                                    BotRuntime.stopBot(this, bot.id)
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "Error stopping ${bot.name}",
                                        e
                                    )
                                }
                            }
                        }
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BotHost runtime",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
