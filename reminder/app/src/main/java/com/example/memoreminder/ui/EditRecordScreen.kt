@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.memoreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.util.TimeUtil
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * 新增 / 编辑记录。
 *
 * 事件时间默认取「明天 09:00」；默认提醒固定在事件前一天 21:00，
 * 也可以再开一个自定义提醒时间（两个时间都会响）。
 */
@Composable
fun EditRecordScreen(
    existing: Reminder?,
    onDone: (text: String, eventTime: Long, customAlarmTime: Long?) -> Unit,
    onCancel: () -> Unit
) {
    val zone = TimeUtil.zone()
    val initialEvent = remember(existing?.id) {
        if (existing != null) {
            Instant.ofEpochMilli(existing.eventTime).atZone(zone)
        } else {
            LocalDate.now().plusDays(1).atTime(9, 0).atZone(zone)
        }
    }
    val initialCustom = remember(existing?.id) {
        val millis = existing?.customAlarmTime
        if (millis != null) Instant.ofEpochMilli(millis).atZone(zone) else initialEvent
    }

    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    var eventDate by remember { mutableStateOf(initialEvent.toLocalDate()) }
    var eventClock by remember { mutableStateOf(initialEvent.toLocalTime().withSecond(0).withNano(0)) }
    var useCustom by remember { mutableStateOf(existing?.customAlarmTime != null) }
    var customDate by remember { mutableStateOf(initialCustom.toLocalDate()) }
    var customClock by remember { mutableStateOf(initialCustom.toLocalTime().withSecond(0).withNano(0)) }
    var error by remember { mutableStateOf<String?>(null) }

    var showEventDatePicker by remember { mutableStateOf(false) }
    var showEventTimePicker by remember { mutableStateOf(false) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    var showCustomTimePicker by remember { mutableStateOf(false) }

    val defaultHint = if (eventDate.isAfter(LocalDate.now())) {
        "默认提醒：${TimeUtil.formatDate(eventDate.minusDays(1))} 21:00"
    } else {
        "事件就在今天或更早，没有默认提醒，请自行设置提醒时间"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (existing == null) "新增记录" else "编辑记录",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("备忘内容") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("事件时间", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showEventDatePicker = true }) {
                Text(TimeUtil.formatDate(eventDate))
            }
            OutlinedButton(onClick = { showEventTimePicker = true }) {
                Text(TimeUtil.formatClock(eventClock))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = defaultHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useCustom, onCheckedChange = { useCustom = it })
            Spacer(Modifier.width(12.dp))
            Text("另设提醒时间（与默认提醒同时生效）")
        }
        if (useCustom) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showCustomDatePicker = true }) {
                    Text(TimeUtil.formatDate(customDate))
                }
                OutlinedButton(onClick = { showCustomTimePicker = true }) {
                    Text(TimeUtil.formatClock(customClock))
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val trimmed = text.trim()
                val eventMillis = TimeUtil.toMillis(eventDate, eventClock)
                val customMillis = if (useCustom) TimeUtil.toMillis(customDate, customClock) else null
                val now = System.currentTimeMillis()
                val message = when {
                    trimmed.isEmpty() -> "请输入备忘内容"
                    eventMillis <= now -> "事件时间需要晚于当前时间"
                    customMillis != null && customMillis <= now -> "提醒时间需要晚于当前时间"
                    else -> null
                }
                error = message
                if (message == null) {
                    onDone(trimmed, eventMillis, customMillis)
                }
            }) { Text("保存") }

            TextButton(onClick = onCancel) { Text("取消") }
        }
    }

    if (showEventDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtil.toUtcDateMillis(eventDate)
        )
        DatePickerDialog(
            onDismissRequest = { showEventDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { eventDate = TimeUtil.fromUtcDateMillis(it) }
                    showEventDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showEventDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEventTimePicker) {
        val state = rememberTimePickerState(
            initialHour = eventClock.hour,
            initialMinute = eventClock.minute,
            is24Hour = true
        )
        TimePickerDialogLite(
            state = state,
            onConfirm = {
                eventClock = LocalTime.of(state.hour, state.minute)
                showEventTimePicker = false
            },
            onDismiss = { showEventTimePicker = false }
        )
    }

    if (showCustomDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = TimeUtil.toUtcDateMillis(customDate)
        )
        DatePickerDialog(
            onDismissRequest = { showCustomDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customDate = TimeUtil.fromUtcDateMillis(it) }
                    showCustomDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showCustomTimePicker) {
        val state = rememberTimePickerState(
            initialHour = customClock.hour,
            initialMinute = customClock.minute,
            is24Hour = true
        )
        TimePickerDialogLite(
            state = state,
            onConfirm = {
                customClock = LocalTime.of(state.hour, state.minute)
                showCustomTimePicker = false
            },
            onDismiss = { showCustomTimePicker = false }
        )
    }
}

/**
 * material3 1.3 只提供 [TimePicker]，没有现成的对话框包装，
 * 这里用 [Dialog] 自己包一层，避免依赖更高版本的 API。
 */
@Composable
private fun TimePickerDialogLite(
    state: TimePickerState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择时间",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onConfirm) { Text("确定") }
                }
            }
        }
    }
}
