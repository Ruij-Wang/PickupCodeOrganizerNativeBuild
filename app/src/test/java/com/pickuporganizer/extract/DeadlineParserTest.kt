package com.pickuporganizer.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DeadlineParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val postedAt = LocalDateTime.of(2026, 5, 8, 10, 30).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun parsesTodayAndTomorrow() {
        val today = DeadlineParser.parseDeadlineMillis("请今日前领取", postedAt, zone)
        val tomorrow = DeadlineParser.parseDeadlineMillis("请明日领取", postedAt, zone)

        assertEquals(LocalDateTime.of(2026, 5, 8, 20, 0).atZone(zone).toInstant().toEpochMilli(), today)
        assertEquals(LocalDateTime.of(2026, 5, 9, 20, 0).atZone(zone).toInstant().toEpochMilli(), tomorrow)
    }

    @Test
    fun parsesExplicitDate() {
        val deadline = DeadlineParser.parseDeadlineMillis("包裹保管至5月10日", postedAt, zone)

        assertEquals(LocalDateTime.of(2026, 5, 10, 20, 0).atZone(zone).toInstant().toEpochMilli(), deadline)
    }

    @Test
    fun returnsNullWhenDeadlineIsAbsent() {
        assertNull(DeadlineParser.parseDeadlineMillis("快递已到驿站,取件码 6-2230", postedAt, zone))
        assertNotNull(DeadlineParser.defaultReminderMillis(postedAt))
    }
}
