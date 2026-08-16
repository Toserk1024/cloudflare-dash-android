package io.github.toserk1024.cfdash.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 安全概况：回源 / 命中 / 缓解（24h 汇总） */
data class SecurityOverview(
    val origin: Long = 0,
    val cached: Long = 0,
    val mitigated: Long = 0
) {
    val total: Long get() = origin + cached + mitigated
    fun pct(v: Long): Float = if (total > 0) v.toFloat() / total else 0f
}

/** 24h 安全趋势点（按小时）：总请求 + 缓解 */
data class SecurityTrendPoint(
    val label: String,
    val requests: Long,
    val mitigated: Long
)

/** 安全日志条目 */
data class SecurityLogEntry(
    val datetime: String,
    val action: String,
    val source: String?,
    val clientIP: String?,
    val clientCountry: String?,
    val deviceType: String?,
    val httpVersion: String?,
    val cacheStatus: String?,
    val host: String?
)

/** 分组结果项 */
data class SecurityBreakdownItem(val name: String, val count: Long)

/** 分组数据源数据集 */
enum class SecurityDataset { HTTP_ADAPTIVE, FIREWALL_ADAPTIVE }

/**
 * 安全分组视图维度。
 * 注意：来源浏览器 / 来源操作系统 因 httpRequestsAdaptiveGroups 无现成维度字段（不做 UA 本地解析），
 * 已按用户确认从分组选项中删除。
 */
enum class SecurityGroupBy(val label: String, val dimension: String?, val dataset: SecurityDataset) {
    ALL("全部", null, SecurityDataset.HTTP_ADAPTIVE),
    COUNTRY("国家", "clientCountryName", SecurityDataset.HTTP_ADAPTIVE),
    DEVICE("客户端设备类型", "clientDeviceType", SecurityDataset.HTTP_ADAPTIVE),
    IP("客户端 IP", "clientIP", SecurityDataset.HTTP_ADAPTIVE),
    HTTP_VERSION("HTTP 版本", "clientRequestHTTPProtocol", SecurityDataset.HTTP_ADAPTIVE),
    CACHE_STATUS("缓存状态", "cacheStatus", SecurityDataset.HTTP_ADAPTIVE),
    ACTION("安全性操作", "action", SecurityDataset.FIREWALL_ADAPTIVE),
    SOURCE("安全性来源", "source", SecurityDataset.FIREWALL_ADAPTIVE)
}

/** 缓解动作集合（其余如 allow/log/unknown 及 solved/bypassed 类不计入缓解） */
private val MITIGATE_ACTIONS = setOf(
    "block", "challenge", "jschallenge", "managedchallenge", "connectionclose",
    "managedchallengenoninteractivesolved", "managedchallengeinteractivesolved",
    "precursorinterstitialpageissued"
)

/**
 * 安全分析 GraphQL 查询构建与响应解析。
 * schema 结论（Cloudflare 官方文档核对）：
 * - 概况/趋势用 httpRequests1hGroups（sum 支持 requests/cachedRequests，按 datetime 小时分组，24h 窗口）
 * - 缓解用 firewallEventsAdaptiveGroups（按 datetimeHour + action 分组，累加缓解动作）
 * - 分组分布（国家/设备/IP/HTTP版本/缓存状态）用 httpRequestsAdaptiveGroups（count_DESC）
 * - 分组分布（操作/来源）与日志用 firewallEventsAdaptive(Groups)
 * - 安全事件为采样数据，页面需提示
 */
object SecurityAnalyticsParser {

    private const val H1_GROUPS = "httpRequests1hGroups"
    private const val HTTP_ADAPTIVE_GROUPS = "httpRequestsAdaptiveGroups"
    private const val FW_ADAPTIVE_GROUPS = "firewallEventsAdaptiveGroups"
    private const val FW_ADAPTIVE = "firewallEventsAdaptive"

    private const val LOG_LIMIT = 200

    // ===== 查询构建 =====

    /** httpRequests1hGroups：24h 按小时，返回 requests + cachedRequests（概况命中 + 趋势） */
    fun overviewQuery(zoneId: String): String {
        val (s, e) = window24h()
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(H1_GROUPS).append("(limit: 48, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
            append(" dimensions { datetime }\n sum { requests cachedRequests }\n }\n }\n }\n }")
        }
    }

    /** firewallEventsAdaptiveGroups：24h 缓解，按 datetimeHour + action 分组 */
    fun mitigationQuery(zoneId: String): String {
        val (s, e) = window24h()
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(FW_ADAPTIVE_GROUPS).append("(limit: 200, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
            append(" count\n dimensions { datetimeHour action }\n }\n }\n }\n }")
        }
    }

    /** 分组分布查询（按所选维度，Top N） */
    fun breakdownQuery(zoneId: String, groupBy: SecurityGroupBy): String {
        val dim = groupBy.dimension ?: return overviewQuery(zoneId)
        val dataset = if (groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE) FW_ADAPTIVE_GROUPS else HTTP_ADAPTIVE_GROUPS
        val (s, e) = window24h()
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(dataset).append("(limit: 20, orderBy: [count_DESC], filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
            append(" count\n dimensions { $dim }\n }\n }\n }\n }")
        }
    }

    /** 日志查询：firewallEventsAdaptive 最近 N 条（拉取后客户端按筛选过滤） */
    fun logsQuery(zoneId: String): String {
        val (s, e) = window24h()
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(FW_ADAPTIVE).append("(limit: $LOG_LIMIT, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
            append(" datetime action source clientIP clientCountry clientDeviceType clientHTTPProtocol cacheStatus clientRequestHost rayId\n }\n }\n }\n }")
        }
    }

    // ===== 解析 =====

    /** 解析 httpRequests1hGroups：按小时趋势 + 总请求/命中 */
    fun parseHttpHourly(root: JsonElement): Pair<List<SecurityTrendPoint>, SecurityOverview> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return emptyList() to SecurityOverview()
        val groups = zones.firstOrNull()?.jsonObject?.get(H1_GROUPS) as? JsonArray ?: return emptyList() to SecurityOverview()
        var requests = 0L
        var cached = 0L
        val byLabel = LinkedHashMap<String, Long>()
        groups.forEach { g ->
            val o = g.jsonObject
            val dims = o["dimensions"]?.jsonObject
            val s = o["sum"] as? JsonObject ?: return@forEach
            val r = s["requests"]?.jsonPrimitive?.longOrNull ?: 0L
            val c = s["cachedRequests"]?.jsonPrimitive?.longOrNull ?: 0L
            requests += r
            cached += c
            val label = dims?.get("datetime")?.jsonPrimitive?.content?.let(::formatHourLabel) ?: return@forEach
            byLabel.merge(label, r) { a, b -> a + b }
        }
        val trend = byLabel.map { (k, v) -> SecurityTrendPoint(label = k, requests = v, mitigated = 0) }
        return trend to SecurityOverview(origin = 0, cached = cached, mitigated = 0).copy(origin = (requests - cached).coerceAtLeast(0))
    }

    /** 解析 firewallEventsAdaptiveGroups：缓解按小时 + 总缓解（仅统计缓解动作） */
    fun parseMitigation(root: JsonElement): Pair<List<SecurityTrendPoint>, Long> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return emptyList() to 0L
        val groups = zones.firstOrNull()?.jsonObject?.get(FW_ADAPTIVE_GROUPS) as? JsonArray ?: return emptyList() to 0L
        val byLabel = LinkedHashMap<String, Long>()
        var total = 0L
        groups.forEach { g ->
            val o = g.jsonObject
            val dims = o["dimensions"]?.jsonObject ?: return@forEach
            val action = dims["action"]?.jsonPrimitive?.content ?: return@forEach
            if (action !in MITIGATE_ACTIONS) return@forEach
            val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
            total += count
            val label = dims["datetimeHour"]?.jsonPrimitive?.content?.let(::formatHourLabel) ?: return@forEach
            byLabel.merge(label, count) { a, b -> a + b }
        }
        val trend = byLabel.map { (k, v) -> SecurityTrendPoint(label = k, requests = 0, mitigated = v) }
        return trend to total
    }

    /** 解析分组分布（Top N，按 count 降序） */
    fun parseBreakdown(root: JsonElement, dataset: SecurityDataset): List<SecurityBreakdownItem> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return emptyList()
        val groups = zones.firstOrNull()?.jsonObject
            ?.get(if (dataset == SecurityDataset.FIREWALL_ADAPTIVE) FW_ADAPTIVE_GROUPS else HTTP_ADAPTIVE_GROUPS) as? JsonArray
            ?: return emptyList()
        val items = mutableListOf<SecurityBreakdownItem>()
        groups.forEach { g ->
            val o = g.jsonObject
            val dims = o["dimensions"]?.jsonObject ?: return@forEach
            val name = dims.values.firstOrNull()?.jsonPrimitive?.content ?: return@forEach
            if (name.isBlank()) return@forEach
            items.add(SecurityBreakdownItem(name, o["count"]?.jsonPrimitive?.longOrNull ?: 0L))
        }
        return items.sortedByDescending { it.count }
    }

    /** 解析安全日志列表 */
    fun parseLogs(root: JsonElement): List<SecurityLogEntry> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return emptyList()
        val events = zones.firstOrNull()?.jsonObject?.get(FW_ADAPTIVE) as? JsonArray ?: return emptyList()
        return events.mapNotNull { e ->
            val o = e.jsonObject
            SecurityLogEntry(
                datetime = o["datetime"]?.jsonPrimitive?.content?.let(::formatLogTime) ?: "",
                action = o["action"]?.jsonPrimitive?.content ?: "",
                source = o["source"]?.jsonPrimitive?.content,
                clientIP = o["clientIP"]?.jsonPrimitive?.content,
                clientCountry = o["clientCountry"]?.jsonPrimitive?.content,
                deviceType = o["clientDeviceType"]?.jsonPrimitive?.content,
                httpVersion = o["clientHTTPProtocol"]?.jsonPrimitive?.content,
                cacheStatus = o["cacheStatus"]?.jsonPrimitive?.content,
                host = o["clientRequestHost"]?.jsonPrimitive?.content
            )
        }
    }

    // ===== 工具 =====

    private fun window24h(): Pair<String, String> {
        val now = System.currentTimeMillis()
        return isoUtc(now - 24 * 3600_000L) to isoUtc(now)
    }

    private fun isoUtc(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ts))

    /** 小时标签：MM-dd HH时 */
    private fun formatHourLabel(iso: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso) ?: return iso
        SimpleDateFormat("MM-dd HH时", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(parsed)
    } catch (e: Exception) {
        iso
    }

    /** 日志时间：MM-dd HH:mm:ss */
    private fun formatLogTime(iso: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso) ?: return iso
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(parsed)
    } catch (e: Exception) {
        iso
    }
}
