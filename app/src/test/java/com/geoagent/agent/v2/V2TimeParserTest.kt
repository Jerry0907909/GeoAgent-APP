package com.geoagent.agent.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.TimeZone

class V2TimeParserTest {

    private val clock = FixedRuntimeClock(
        nowMillis = 1_725_257_600_000L,
        zone = TimeZone.getTimeZone("Asia/Shanghai")
    )

    @Test
    fun parsesTomorrowAfternoonClockTime() {
        val window = parseV2TimeWindow("把明天下午3点会议加入日历", clock)

        assertNotNull(window)
        window!!
        assertEquals(1_725_346_800_000L, window.beginMillis)
        assertEquals(1_725_350_400_000L, window.endMillis)
    }

    @Test
    fun parsesMorningDefaultHour() {
        val window = parseV2TimeWindow("明早提醒我提交报告", clock)

        assertNotNull(window)
        window!!
        assertEquals(1_725_325_200_000L, window.beginMillis)
    }

    private class FixedRuntimeClock(
        private val nowMillis: Long,
        private val zone: TimeZone
    ) : V2RuntimeClock {
        override fun nowMillis(): Long = nowMillis
        override fun timeZone(): TimeZone = zone
    }
}
