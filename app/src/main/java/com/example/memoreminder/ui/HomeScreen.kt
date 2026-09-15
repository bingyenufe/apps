@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.memoreminder.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.util.TimeUtil
import com.example.memoreminder.viewmodel.ReminderViewModel
import java.time.LocalDate

@Composable
fun HomeScreen(viewModel: ReminderViewModel) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }

    val now = System.currentTimeMillis()
    val today = LocalDate.now()

    val todayItems = reminders
        .filter { TimeUtil.localDateOf(it.eventTime) == today }
        .sortedBy { it.eventTime }
    val upcomingItems = reminders.filter { it.eventTime > now }.sortedBy { it.eventTime }
    val historyItems = reminders.filter { it.eventTime <= now }.sortedByDescending { it.eventTime }

    Scaffold(
        topBar = { TopAppBar(title = { Text("备忘提醒") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingId = null
                showEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "新增记录")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PermissionBanners()

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("今天") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("全部") })
            }

            val onEdit: (Reminder) -> Unit = { reminder ->
                editingId = reminder.id
                showEditor = true
            }
            val onDelete: (Reminder) -> Unit = { pendingDelete = it }

            if (selectedTab == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (todayItems.isEmpty()) {
                        item { EmptyHint("今天没有安排") }
                    } else {
                        items(todayItems, key = { it.id }) { reminder ->
                            ReminderRow(reminder, now, onEdit, onDelete)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { SectionHeader("即将到来") }
                    if (upcomingItems.isEmpty()) {
                        item { EmptyHint("没有待提醒的记录") }
                    } else {
                        items(upcomingItems, key = { "up_${it.id}" }) { reminder ->
                            ReminderRow(reminder, now, onEdit, onDelete)
                        }
                    }
                    item { SectionHeader("历史") }
                    if (historyItems.isEmpty()) {
                        item { EmptyHint("暂无历史记录") }
                    } else {
                        items(historyItems, key = { "his_${it.id}" }) { reminder ->
                            ReminderRow(reminder, now, onEdit, onDelete)
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val editing = editingId?.let { id -> reminders.firstOrNull { it.id == id } }
        EditRecordScreen(
            existing = editing,
            onDone = { text, eventTime, customAlarmTime ->
                val id = editing?.id
                if (id != null) {
                    viewModel.update(id, text, eventTime, customAlarmTime)
                } else {
                    viewModel.add(text, eventTime, customAlarmTime)
                }
                showEditor = false
            },
            onCancel = { showEditor = false }
        )
    }

    pendingDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记录") },
            text = { Text("确定删除「${reminder.text}」吗？") },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(reminder.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
    )
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    now: Long,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    val status = when {
        reminder.fired -> "已提醒"
        reminder.eventTime < now -> "已过"
        else -> "待提醒"
    }
    val statusColor = when (status) {
        "已提醒" -> Color(0xFF2E7D32)
        "已过" -> Color(0xFF757575)
        else -> MaterialTheme.colorScheme.primary
    }

    ListItem(
        modifier = Modifier.clickable { onEdit(reminder) },
        headlineContent = { Text(reminder.text) },
        supportingContent = {
            val custom = reminder.customAlarmTime
                ?.let { " · 自定义提醒 ${TimeUtil.formatDateTime(it)}" }
                .orEmpty()
            Text(TimeUtil.formatDateTime(reminder.eventTime) + custom)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status,
                    color = statusColor,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                IconButton(onClick = { onDelete(reminder) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    )
}

@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }

    val needsExactAlarm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == false
        } else {
            false
        }
    }
    val notificationsDisabled = remember {
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    if (needsExactAlarm) {
        Banner(
            text = "需要允许「闹钟和提醒」权限，闹钟才能准点响",
            actionText = "去设置",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            }
        )
    }

    if (notificationsDisabled) {
        Banner(
            text = "通知权限未开启，到点将无法提醒",
            actionText = "去设置",
            onAction = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
        )
    }
}

@Composable
private fun Banner(text: String, actionText: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}
