package io.github.toserk1024.cfdash.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** GraphQL 统计解析的纯函数测试（不涉及网络与 Android 环境） */
class AnalyticsParserTest {

    private fun json(raw: String) = Json.parseToJsonElement(raw)

    @Test
    fun parseZoneSumsRequestsCachedBytesThreatsAndUniques() {
        val root = json(
            """
            {"data":{"viewer":{"zones":[{
              "httpRequests1hGroups":[
                {"sum":{"requests":10,"cachedRequests":4,"bytes":100,"threats":1,"cachedBytes":40},"uniq":{"uniques":3}},
                {"sum":{"requests":5,"cachedRequests":1,"bytes":50,"threats":2,"cachedBytes":10},"uniq":{"uniques":2}}
              ]
            }]}}}
            """.trimIndent()
        )
        val sum = AnalyticsParser.parseZone(root, AnalyticsRange.H24)
        assertEquals(15L, sum.requests)
        assertEquals(5L, sum.cachedRequests)
        assertEquals(150L, sum.bytes)
        assertEquals(3L, sum.threats)
        assertEquals(5L, sum.uniques)
    }

    @Test
    fun parseZoneReturnsZerosWhenDataMissing() {
        val sum = AnalyticsParser.parseZone(json("{}"), AnalyticsRange.D7)
        assertEquals(0L, sum.requests)
        assertEquals(0L, sum.cachedRequests)
        assertEquals(0L, sum.uniques)
    }

    @Test
    fun parseZoneSeriesFormatsUtcLabelFor24h() {
        val root = json(
            """
            {"data":{"viewer":{"zones":[{
              "httpRequests1hGroups":[
                {"dimensions":{"datetime":"2026-08-16T05:00:00Z"},"sum":{"requests":7},"uniq":{"uniques":1}}
              ]
            }]}}}
            """.trimIndent()
        )
        val series = AnalyticsParser.parseZoneSeries(root, AnalyticsRange.H24)
        assertEquals(1, series.points.size)
        assertEquals("08-16 05时", series.points[0].label)
        assertEquals(7L, series.points[0].requests)
    }

    @Test
    fun parseZoneSeriesFormatsDateLabelFor7d() {
        val root = json(
            """
            {"data":{"viewer":{"zones":[{
              "httpRequests1dGroups":[
                {"dimensions":{"date":"2026-08-10"},"sum":{"requests":3},"uniq":{"uniques":1}}
              ]
            }]}}}
            """.trimIndent()
        )
        val series = AnalyticsParser.parseZoneSeries(root, AnalyticsRange.D7)
        assertEquals(1, series.points.size)
        assertEquals("08-10", series.points[0].label)
    }

    @Test
    fun accountSeriesMergesSameLabelAcrossZones() {
        val root = json(
            """
            {"data":{"viewer":{"accounts":[{"zones":[
              {"httpRequests1hGroups":[{"dimensions":{"datetime":"2026-08-16T05:00:00Z"},"sum":{"requests":4}}]},
              {"httpRequests1hGroups":[{"dimensions":{"datetime":"2026-08-16T05:00:00Z"},"sum":{"requests":6}}]}
            ]}]}}}
            """.trimIndent()
        )
        val series = AnalyticsParser.parseAccountSeries(root, AnalyticsRange.H24)
        assertEquals(1, series.points.size)
        assertEquals(10L, series.points[0].requests)
    }

    @Test
    fun cacheHitRatioIsCachedOverRequests() {
        val sum = AnalyticsSum(requests = 200, cachedRequests = 50)
        assertEquals(0.25f, sum.cacheHitRatio, 0.0001f)
    }

    @Test
    fun cacheHitRatioIsZeroWhenNoRequests() {
        assertEquals(0f, AnalyticsSum().cacheHitRatio, 0.0001f)
    }
}