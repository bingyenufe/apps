package com.example.memoreminder.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.data.ReminderDatabase
import com.example.memoreminder.notify.NotificationHelper
import com.example.memoreminder.scheduler.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 响铃期间的前台服务：循环播放系统默认闹钟音，并在通知栏常驻「关闭提醒」按钮，
 * 直到用户手动关闭（[AlarmReceiver.ACTION_STOP]）或记录被删除 / 改期。
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(AlarmReceiver.EXTRA_CODE, 0) ?: 0
        if (code == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val ids = intent?.getLongArrayExtra(AlarmReceiver.EXTRA_IDS) ?: LongArray(0)

        scope.launch {
            val reminders = loadReminders(ids)
            val notification = NotificationHelper.buildAlarmNotification(
                this@AlarmService,
                code,
                reminders,
                ongoing = true,
                silent = true
            )
            val foreground = runCatching { startForeground(code, notification) }.isSuccess
            if (!foreground) {
                // 系统不允许该类型的前台服务时，退回普通通知提醒
                NotificationHelper.postAlarmNotification(this@AlarmService, code, reminders)
                stopSelf()
                return@launch
            }

            // 续响的循环闹钟会重复调用 onStartCommand，此时只刷新通知，不重启声音
            if (activeCode != code) {
                activeCode = code
                startSound()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun loadReminders(ids: LongArray): List<Reminder> {
        if (ids.isEmpty()) return emptyList()
        val dao = ReminderDatabase.get(this).reminders()
        return ids.mapNotNull { dao.byId(it) }
    }

    private fun startSound() {
        stopSound()

        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return

        if (!playWithMediaPlayer(uri)) {
            playWithRingtone(uri)
        }
    }

    /** 循环播放，做到「一直响」；失败时返回 false 交给 Ringtone 兜底。 */
    private fun playWithMediaPlayer(uri: Uri): Boolean {
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer.setDataSource(this, uri)
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            true
        } catch (e: Exception) {
            runCatching { mediaPlayer.release() }
            player = null
            false
        }
    }

    private fun playWithRingtone(uri: Uri) {
        try {
            val tone = RingtoneManager.getRingtone(this, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tone.isLooping = true
            }
            tone.play()
            ringtone = tone
        } catch (e: Exception) {
            ringtone = null
        }
    }

    private fun stopSound() {
        val currentPlayer = player
        player = null
        if (currentPlayer != null) {
            runCatching {
                if (currentPlayer.isPlaying) currentPlayer.stop()
                currentPlayer.release()
            }
        }

        val currentTone = ringtone
        ringtone = null
        if (currentTone != null) {
            runCatching {
                if (currentTone.isPlaying) currentTone.stop()
            }
        }
    }

    override fun onDestroy() {
        stopSound()
        activeCode = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * 当前正在响铃的闹钟 code，供 [com.example.memoreminder.scheduler.AlarmScheduler]
         * 判断记录被删除 / 改期时是否需要停止响铃。
         */
        @Volatile
        var activeCode: Int? = null
            private set
    }
}
