package com.example.memoreminder.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.memoreminder.data.ReminderDatabase

/** 开机或应用更新后重新对齐闹钟。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val dao = ReminderDatabase.get(context).reminders()
                AlarmScheduler(context, dao).sync()
            }
        }
    }
}
