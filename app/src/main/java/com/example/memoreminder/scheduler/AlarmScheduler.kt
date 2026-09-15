package com.example.memoreminder.scheduler

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.data.ReminderDao
import com.example.memoreminder.service.AlarmService
import com.example.memoreminder.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 闹钟对齐器。
 *
 * 规则：
 * - 每条记录默认在「事件日期前一天 21:00」响一次；若事件就在今天或更早，则没有默认提醒。
 * - 若设置了自定义提醒时间，则额外响一次（两个时间都会响）。
 * - 同一时刻的多条记录合并成一次闹钟，通知里逐条列出。
 *
 * [sync] 是幂等的：先取消所有旧闹钟，再按当前数据库内容重新排布未来的闹钟。
 */
class AlarmScheduler(
    private val context: Context,
    private val dao: ReminderDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private data class Scheduled(val code: Int, val moment: Long)

    /** 重新对齐全部闹钟。新增 / 修改 / 删除记录后以及应用启动时调用。 */
    fun sync() {
        scope.launch {
            val now = System.currentTimeMillis()
            val reminders = dao.getAll()

            // 时刻 -> 该时刻需要提醒的记录 id
            val byMoment = LinkedHashMap<Long, MutableList<Long>>()
            for (reminder in reminders) {
                for (moment in alarmMoments(reminder)) {
                    if (moment > now) {
                        byMoment.getOrPut(moment) { mutableListOf() }.add(reminder.id)
                    }
                }
            }

            val old = readScheduled()
            val oldCodeByMoment = old.associate { it.moment to it.code }

            // 当前数据里仍然存在的所有时刻（含已过去的时刻，用于识别「已触发、可能仍在响」的闹钟）
            val allMoments = HashSet<Long>()
            for (reminder in reminders) {
                allMoments.addAll(alarmMoments(reminder))
            }

            // 1. 只取消未来的旧闹钟（它们马上会被重排）；已过去的闹钟保持原样，
            //    否则打开应用会把正在响的闹钟静音。
            for (entry in old) {
                if (entry.moment > now) {
                    cancelPending(AlarmReceiver.ACTION_FIRE, entry.code)
                    cancelPending(AlarmReceiver.ACTION_LOOP, entry.code + LOOP_CODE_OFFSET)
                }
            }

            // 2. 记录已被删除或改期（该时刻不再对应任何记录）：停止响铃并撤掉通知
            for (entry in old) {
                if (!allMoments.contains(entry.moment)) {
                    stopRinging(entry.code)
                }
            }

            // 3. 重新排布未来的闹钟（同一时刻复用原来的 code，便于取消与通知去重）
            var counter = prefs.getInt(KEY_COUNTER, FIRST_CODE)
            val fresh = mutableListOf<Scheduled>()

            // 已触发但仍对应现有记录的时刻：保留条目，这样之后删除/改期时还能停掉正在响的闹钟
            for (entry in old) {
                if (entry.moment <= now && allMoments.contains(entry.moment)) {
                    fresh.add(entry)
                }
            }

            for ((moment, ids) in byMoment.entries.sortedBy { it.key }) {
                val code = oldCodeByMoment[moment] ?: counter++
                scheduleAlarm(moment, code, ids)
                fresh.add(Scheduled(code, moment))
            }

            prefs.edit()
                .putInt(KEY_COUNTER, counter)
                .putString(KEY_SCHEDULED, encode(fresh))
                .apply()
        }
    }

    private fun scheduleAlarm(moment: Long, code: Int, reminderIds: List<Long>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_CODE, code)
            .putExtra(AlarmReceiver.EXTRA_MOMENT, moment)
            .putExtra(AlarmReceiver.EXTRA_IDS, reminderIds.toLongArray())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(alarmManager, moment, pendingIntent)
    }

    private fun cancelPending(action: String, code: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.cancel()
    }

    private fun stopRinging(code: Int) {
        if (AlarmService.activeCode == code) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(code)
    }

    private fun readScheduled(): List<Scheduled> {
        val raw = prefs.getString(KEY_SCHEDULED, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                Scheduled(obj.getInt("code"), obj.getLong("moment"))
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<Scheduled>): String {
        val array = JSONArray()
        for (entry in list) {
            array.put(JSONObject().put("code", entry.code).put("moment", entry.moment))
        }
        return array.toString()
    }

    companion object {
        const val DEFAULT_ALARM_HOUR = 21
        const val LOOP_INTERVAL_MS = 3L * 60L * 1000L
        const val LOOP_CODE_OFFSET = 3_000_000

        private const val PREFS = "alarm_scheduler"
        private const val KEY_SCHEDULED = "scheduled"
        private const val KEY_COUNTER = "code_counter"
        private const val FIRST_CODE = 10_000

        /** 默认提醒时刻：事件日期的前一天 21:00；事件不在未来则为 null。 */
        fun defaultAlarmMoment(reminder: Reminder): Long? {
            val eventDay = TimeUtil.localDateOf(reminder.eventTime)
            if (!eventDay.isAfter(LocalDate.now())) return null
            return TimeUtil.toMillis(eventDay.minusDays(1), java.time.LocalTime.of(DEFAULT_ALARM_HOUR, 0))
        }

        /** 一条记录需要触发的所有提醒时刻（默认 + 自定义，去重）。 */
        fun alarmMoments(reminder: Reminder): List<Long> {
            val moments = mutableListOf<Long>()
            defaultAlarmMoment(reminder)?.let { moments.add(it) }
            reminder.customAlarmTime?.let { moments.add(it) }
            return moments.distinct()
        }

        /** 精确闹钟；个别机型禁用精确闹钟时退化为「尽量准时」。 */
        fun setExact(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }
}
