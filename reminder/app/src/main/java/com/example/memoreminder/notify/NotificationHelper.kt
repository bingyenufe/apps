package com.example.memoreminder.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.memoreminder.MainActivity
import com.example.memoreminder.R
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.scheduler.AlarmReceiver
import com.example.memoreminder.util.TimeUtil

object NotificationHelper {

    const val CHANNEL_ALARMS = "channel_alarms"

    private const val OPEN_CODE_OFFSET = 1_000_000
    private const val STOP_CODE_OFFSET = 2_000_000

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ALARMS) != null) return

        val sound = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(
            CHANNEL_ALARMS,
            "闹钟提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "备忘事件的闹钟提醒"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 800L, 600L, 800L, 600L)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 兜底提醒：前台服务无法启动时使用，由系统提示音提醒一次，
     * 之后交给每 3 分钟一次的循环闹钟续响。
     */
    fun postAlarmNotification(context: Context, code: Int, reminders: List<Reminder>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(code, buildAlarmNotification(context, code, reminders, ongoing = false))
    }

    /** 构建闹钟通知：标题 + 同一时刻的全部记录，附带「关闭提醒」按钮。 */
    fun buildAlarmNotification(
        context: Context,
        code: Int,
        reminders: List<Reminder>,
        ongoing: Boolean,
        silent: Boolean = false
    ): Notification {
        ensureChannels(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            code + OPEN_CODE_OFFSET,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            code + STOP_CODE_OFFSET,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_STOP)
                .putExtra(AlarmReceiver.EXTRA_CODE, code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isTest = reminders.isEmpty()
        val title = when {
            isTest -> "备忘提醒 · 测试响铃"
            reminders.size > 1 -> "备忘提醒（${reminders.size} 条）"
            else -> "备忘提醒"
        }
        val body = if (isTest) {
            "这是一次测试响铃。点下面的「关闭提醒」或打开应用点「关闭闹钟」即可停止。"
        } else {
            reminders.joinToString(separator = "\n") { reminder ->
                "• ${reminder.text}（${TimeUtil.formatDateTime(reminder.eventTime)}）"
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, "关闭提醒", stopIntent)
            .setAutoCancel(false)
            .setOngoing(ongoing)
            // 前台服务自己循环播放闹钟音，通知就不再重复响一次
            .setSilent(silent)

        // 息屏 / 锁屏时直接弹出应用界面，保证一定有「关闭」入口
        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(contentIntent, true)
        }

        return builder.build()
    }

    private fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }
}
