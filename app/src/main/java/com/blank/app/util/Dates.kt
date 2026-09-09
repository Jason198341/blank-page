package com.blank.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone)

    fun toDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun atHour(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long = startOfDay(date.plusDays(1)) - 1

    /** 지연 일수. 오늘 것이면 0, 어제 것이면 1 */
    fun daysLate(dueAt: Long, now: Long = System.currentTimeMillis()): Long =
        ChronoUnit.DAYS.between(toDate(dueAt), toDate(now)).coerceAtLeast(0)

    private val md = DateTimeFormatter.ofPattern("M월 d일")
    private val dow = arrayOf("월", "화", "수", "목", "금", "토", "일")

    fun formatDay(date: LocalDate): String = md.format(date)
    fun formatDayDow(date: LocalDate): String = "${md.format(date)} ${dow[date.dayOfWeek.value - 1]}"

    fun formatElapsed(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
