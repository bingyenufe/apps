package com.example.memoreminder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memoreminder.data.Reminder
import com.example.memoreminder.data.ReminderDao
import com.example.memoreminder.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(
    private val dao: ReminderDao,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, eventTime: Long, customAlarmTime: Long?) {
        viewModelScope.launch {
            dao.insert(
                Reminder(
                    text = text,
                    eventTime = eventTime,
                    customAlarmTime = customAlarmTime,
                    fired = false,
                    createdAt = System.currentTimeMillis()
                )
            )
            scheduler.sync()
        }
    }

    fun update(id: Long, text: String, eventTime: Long, customAlarmTime: Long?) {
        viewModelScope.launch {
            val existing = dao.byId(id) ?: return@launch
            val timingChanged = existing.eventTime != eventTime || existing.customAlarmTime != customAlarmTime
            dao.update(
                existing.copy(
                    text = text,
                    eventTime = eventTime,
                    customAlarmTime = customAlarmTime,
                    // 时间改了就当没提醒过，可以重新响一次
                    fired = if (timingChanged) false else existing.fired
                )
            )
            scheduler.sync()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val existing = dao.byId(id) ?: return@launch
            dao.delete(existing)
            scheduler.sync()
        }
    }
}
