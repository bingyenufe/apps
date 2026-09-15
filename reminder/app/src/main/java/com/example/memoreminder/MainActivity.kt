package com.example.memoreminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.memoreminder.data.ReminderDao
import com.example.memoreminder.data.ReminderDatabase
import com.example.memoreminder.scheduler.AlarmScheduler
import com.example.memoreminder.ui.HomeScreen
import com.example.memoreminder.ui.theme.MemoReminderTheme
import com.example.memoreminder.viewmodel.ReminderViewModel

class MainActivity : ComponentActivity() {

    private lateinit var scheduler: AlarmScheduler

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 用户拒绝也不影响记录功能，只是弹不出通知
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = ReminderDatabase.get(applicationContext).reminders()
        scheduler = AlarmScheduler(applicationContext, dao)
        scheduler.sync()

        requestNotificationPermission()

        setContent {
            MemoReminderTheme {
                val viewModel: ReminderViewModel = viewModel(
                    modelClass = ReminderViewModel::class.java,
                    factory = reminderViewModelFactory(dao, scheduler)
                )
                HomeScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次回到应用都重新对齐一次，防止系统清理或时区变化造成遗漏
        scheduler.sync()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun reminderViewModelFactory(
    dao: ReminderDao,
    scheduler: AlarmScheduler
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReminderViewModel(dao, scheduler) as T
    }
}
