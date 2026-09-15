package com.example.memoreminder.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 所有时间都以 epoch 毫秒存储，展示与计算时统一使用设备本地时区。 */
object TimeUtil {

    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun localDateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    fun localTimeOf(millis: Long): LocalTime =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalTime()

    /** 本地日期 + 本地时刻 -> epoch 毫秒。 */
    fun toMillis(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone()).toInstant().toEpochMilli()

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).format(dateTimeFormatter)

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).format(clockFormatter)

    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    fun formatClock(time: LocalTime): String = time.format(clockFormatter)

    // Material3 的 DatePicker 以 UTC 零点表示选中日期，需要单独换算。
    fun toUtcDateMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromUtcDateMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
