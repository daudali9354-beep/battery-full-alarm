package com.example.batteryfull

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder

class BatteryService : Service() {

    private var player: MediaPlayer? = null
    private var silenced = false // user ne Stop dabaya, unplug hone tak chup

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val pct = if (level >= 0) level * 100 / scale else 0
            val full = pct >= 100 || status == BatteryManager.BATTERY_STATUS_FULL

            if (!plugged) {
                silenced = false
                stopSound()
            } else if (full && !silenced) {
                startSound()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Battery monitor", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SOUND) {
            silenced = true
            stopSound()
        }
        return START_STICKY
    }

    private fun startSound() {
        if (player?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@BatteryService, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopSound() {
        player?.run {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BatteryService::class.java).setAction(ACTION_STOP_SOUND),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Battery Full Alarm chalu hai")
            .setContentText("Battery 100% hone par sound bajega")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .addAction(
                Notification.Action.Builder(null, "Sound band karo", stopIntent).build()
            )
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        stopSound()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "battery_monitor"
        const val ACTION_STOP_SOUND = "com.example.batteryfull.STOP_SOUND"
    }
}
