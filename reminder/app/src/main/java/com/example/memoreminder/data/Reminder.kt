package com.example.memoreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条备忘记录。
 *
 * @param id 主键，0 表示由数据库自增分配。
 * @param text 备忘内容。
 * @param eventTime 事件发生时间（epoch 毫秒）。默认提醒 = 事件日期前一天 21:00。
 * @param customAlarmTime 可选的自定义提醒时间（epoch 毫秒），null 表示未设置。
 * @param fired 是否已经响过闹钟，用于展示「已提醒」状态。
 * @param createdAt 创建时间（epoch 毫秒）。
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    val eventTime: Long,
    val customAlarmTime: Long? = null,
    val fired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
