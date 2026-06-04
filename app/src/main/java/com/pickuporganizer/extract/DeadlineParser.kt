package com.pickuporganizer.extract

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DeadlineParser {
    private val dateBeforePattern = Regex("""(\d{1,2})[月/-](\d{1,2})日?(?:前|之前|到期|保管至)""")
    private val keepUntilPattern = Regex("""(?:保管至|请于|最晚)(\d{1,2})[月/-](\d{1,2})日?""")

    fun parseDeadlineMillis(text: String, postedAtMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val postedDate = Instant.ofEpochMilli(postedAtMillis).atZone(zoneId).toLocalDate()
        val normalized = PickupExtractor.normalize(text)

        if (normalized.contains("今日") || normalized.contains("今天")) {
            return endOfDay(postedDate, zoneId)
        }
        if (normalized.contains("明日") || normalized.contains("明天")) {
            return endOfDay(postedDate.plusDays(1), zoneId)
        }

        val match = keepUntilPattern.find(normalized) ?: dateBeforePattern.find(normalized)
        if (match != null) {
            val month = match.groupValues[1].toIntOrNull()
            val day = match.groupValues[2].toIntOrNull()
            if (month != null && day != null) {
                val candidate = LocalDate.of(postedDate.year, month, day)
                val adjusted = if (candidate.isBefore(postedDate)) candidate.plusYears(1) else candidate
                return endOfDay(adjusted, zoneId)
            }
        }

        return null
    }

    fun defaultReminderMillis(postedAtMillis: Long): Long = postedAtMillis + 24L * 60L * 60L * 1000L

    private fun endOfDay(date: LocalDate, zoneId: ZoneId): Long =
        date.atTime(LocalTime.of(20, 0)).atZone(zoneId).toInstant().toEpochMilli()
}
