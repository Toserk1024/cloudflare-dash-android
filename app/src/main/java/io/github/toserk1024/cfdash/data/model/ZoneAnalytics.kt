package io.github.toserk1024.cfdash.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 统计时间范围（数据集 + limit 映射） */
enum class AnalyticsRange(val label: String, val dataset: String, val limit: Int) {
    H24("24小时", "httpRequests1hGroups", 24),
    D7("7天", "httpRequests1dGroups", 7),
    D30("30天", "httpRequests1dGroups", 30)
}

/** 统计聚合结果（GraphQL sum） */
@Serializable
data class AnalyticsSum(
    val requests: Long = 0,
    val threats: Long = 0,
    val bytes: Long = 0,
    val cachedRequests: Long = 0,
    val cachedBytes: Long = 0
) {
    /** 缓存命中率 0~1 */
    val cacheHitRatio: Float get() = if (requests > 0) cachedRequests.toFloat() / requests else 0f

    operator fun plus(other: AnalyticsSum) = AnalyticsSum(
        requests + other.requests,
        threats + other.threats,
        bytes + other.bytes,
        cachedRequests + other.cachedRequests,
        cachedBytes + other.cachedBytes
    )
}

/** GraphQL Analytics 查询构建与响应解析（GraphQL 响应为 data/errors 结构，非 ApiResponse 包装） */
object AnalyticsParser {

    /** 构建域名级统计查询 */
    fun zoneQuery(zoneId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range)).append("}, orderBy: [").append(orderByField(range))
        append("]) {\n sum { requests threats bytes cachedRequests cachedBytes }\n }\n }\n }\n }")
    }

    /** 构建账号级统计查询（遍历账号下所有域名） */
    fun accountQuery(accountId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n accounts(filter: {accountTag: \"$accountId\"}) {\n zones {\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range)).append("}, orderBy: [").append(orderByField(range))
        append("]) {\n sum { requests threats bytes cachedRequests cachedBytes }\n }\n }\n }\n }\n }")
    }

    private fun filterField(range: AnalyticsRange): String {
        val (start, end) = rangeWindow(range)
        return if (range == AnalyticsRange.H24) {
            "datetime_geq: \"$start\", datetime_leq: \"$end\""
        } else {
            "date_geq: \"$start\", date_leq: \"$end\""
        }
    }

    private fun orderByField(range: AnalyticsRange): String =
        if (range == AnalyticsRange.H24) "datetime_ASC" else "date_ASC"

    /** 时间窗口（UTC）：24h → datetime ISO；7d/30d → date */
    private fun rangeWindow(range: AnalyticsRange): Pair<String, String> {
        val now = System.currentTimeMillis()
        return if (range == AnalyticsRange.H24) {
            isoUtc(now - 24 * 3600_000L) to isoUtc(now)
        } else {
            val days = if (range == AnalyticsRange.D30) 30 else 7
            dateUtc(now - days * 24 * 3600_000L) to dateUtc(now)
        }
    }

    private fun isoUtc(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ts))

    private fun dateUtc(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ts))

    /** 解析域名级统计：data.viewer.zones[0][dataset][] 累加 */
    fun parseZone(root: JsonElement, range: AnalyticsRange): AnalyticsSum {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return AnalyticsSum()
        return zones.firstOrNull()?.let { accumulate(it.jsonObject, range.dataset) } ?: AnalyticsSum()
    }

    /** 解析账号级统计：data.viewer.accounts[0].zones[] 各域名累加 */
    fun parseAccount(root: JsonElement, range: AnalyticsRange): AnalyticsSum {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("accounts")
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("zones") as? JsonArray
            ?: return AnalyticsSum()
        return zones.fold(AnalyticsSum()) { acc, zone -> acc + accumulate(zone.jsonObject, range.dataset) }
    }

    /** 累加单个节点的数据集 sum */
    private fun accumulate(container: JsonObject, dataset: String): AnalyticsSum {
        var sum = AnalyticsSum()
        (container[dataset] as? JsonArray)?.forEach { g ->
            val s = g.jsonObject["sum"] as? JsonObject ?: return@forEach
            sum += AnalyticsSum(
                requests = s["requests"]?.jsonPrimitive?.longOrNull ?: 0,
                threats = s["threats"]?.jsonPrimitive?.longOrNull ?: 0,
                bytes = s["bytes"]?.jsonPrimitive?.longOrNull ?: 0,
                cachedRequests = s["cachedRequests"]?.jsonPrimitive?.longOrNull ?: 0,
                cachedBytes = s["cachedBytes"]?.jsonPrimitive?.longOrNull ?: 0
            )
        }
        return sum
    }
}