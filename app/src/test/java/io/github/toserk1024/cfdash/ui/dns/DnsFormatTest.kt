package io.github.toserk1024.cfdash.ui.dns

import org.junit.Assert.assertEquals
import org.junit.Test

/** TTL 人类可读格式化测试 */
class DnsFormatTest {

    @Test
    fun secondsUnderOneMinute() {
        assertEquals("30秒", formatTtl(30))
    }

    @Test
    fun minutesUnderOneHour() {
        assertEquals("5分钟", formatTtl(300))
    }

    @Test
    fun exactHour() {
        assertEquals("1小时", formatTtl(3600))
    }

    @Test
    fun hourWithRemainingMinutes() {
        assertEquals("1小时1分钟", formatTtl(3660))
    }

    @Test
    fun exactDay() {
        assertEquals("1天", formatTtl(86400))
    }

    @Test
    fun dayWithRemainingHours() {
        assertEquals("1天1小时", formatTtl(90000))
    }
}