package com.example.memoreminder.scheduler

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.data.ReminderDatabase
import com.example.memoreminder.notify.NotificationHelper
import com.example.memoreminder.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟触发入口。
 *
 * - [ACTION_FIRE]：到点了。
 * - [ACTION_LOOP]：用户还没关闭，续响一次（同时兜底：进程被杀后可重新拉起服务）。
 * - [ACTION_STOP]：用户点了「关闭提醒」，停止响铃并取消后续续响。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE, ACTION_LOOP -> fire(context, intent)
            ACTION_STOP -> stop(context, intent)
        }
    }

    private fun fire(context: Context, intent: Intent) {
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val moment = intent.getLongExtra(EXTRA_MOMENT, 0L)
        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: LongArray(0)
        if (code == 0) return

        val wakeLock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "memoreminder:alarm")
            ?.apply { acquire(30_000L) }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = ReminderDatabase.get(context).reminders()
                if (ids.isNotEmpty()) dao.markFired(ids)
                val reminders = ids.mapNotNull { dao.byId(it) }
                if (reminders.isEmpty()) return@launch

                startRinging(context, code, moment, ids, reminders)
                scheduleLoop(context, code, moment, ids)
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                pendingResult.finish()
            }
        }
    }

    /** 优先用前台服务持续响铃；被系统限制时退回普通高优先级通知。 */
    private fun startRinging(
        context: Context,
        code: Int,
        moment: Long,
        ids: LongArray,
        reminders: List<Reminder>
    ) {
        val intent = Intent(context, AlarmService::class.java)
            .putExtra(EXTRA_CODE, code)
            .putExtra(EXTRA_MOMENT, moment)
            .putExtra(EXTRA_IDS, ids)
        val started = runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.isSuccess
        if (!started) {
            NotificationHelper.postAlarmNotification(context, code, reminders)
        }
    }

    private fun scheduleLoop(context: Context, code: Int, moment: Long, ids: LongArray) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_LOOP)
            .putExtra(EXTRA_CODE, code)
            .putExtra(EXTRA_MOMENT, moment)
            .putExtra(EXTRA_IDS, ids)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code + AlarmScheduler.LOOP_CODE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        AlarmScheduler.setExact(
            alarmManager,
            System.currentTimeMillis() + AlarmScheduler.LOOP_INTERVAL_MS,
            pendingIntent
        )
    }

    private fun stop(context: Context, intent: Intent) {
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        context.stopService(Intent(context, AlarmService::class.java))

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code + AlarmScheduler.LOOP_CODE_OFFSET,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_LOOP),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.cancel()
        context.getSystemService(NotificationManager::class.java)?.cancel(code)
    }

    companion object {
        const val ACTION_FIRE = "com.example.memoreminder.action.ALARM_FIRE"
        const val ACTION_LOOP = "com.example.memoreminder.action.ALARM_LOOP"
        const val ACTION_STOP = "com.example.memoreminder.action.ALARM_STOP"

        const val EXTRA_CODE = "code"
        const val EXTRA_MOMENT = "moment"
        const val EXTRA_IDS = "ids"
    }
}
