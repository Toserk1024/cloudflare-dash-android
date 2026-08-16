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

/** 安全时间范围（最大 24h），作用于概况/趋势/日志/分组 */
enum class SecurityTimeRange(val label: String, val millis: Long) {
    HALF_HOUR("半小时", 30 * 60_000L),
    H3("3小时", 3 * 3600_000L),
    H12("12小时", 12 * 3600_000L),
    H24("1天", 24 * 3600_000L);

    /** 趋势时间粒度（adaptive 维度）：30m/5分钟、3h/15分钟、12h·24h/小时 */
    val trendDimension: String
        get() = when (this) {
            HALF_HOUR -> "datetimeFiveMinutes"
            H3 -> "datetimeFifteenMinutes"
            H12, H24 -> "datetimeHour"
        }
}

/** 分组数据源数据集 */
enum class SecurityDataset { HTTP_ADAPTIVE, FIREWALL_ADAPTIVE }

/** 安全分组视图维度（来源浏览器/操作系统因无现成维度字段已按用户确认删除） */
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

/** 筛选器属性（全局过滤） */
enum class SecurityFilterAttr(val label: String, val field: String, val dataset: SecurityDataset) {
    IP("客户端 IP", "clientIP", SecurityDataset.HTTP_ADAPTIVE),
    COUNTRY("国家", "clientCountryName", SecurityDataset.HTTP_ADAPTIVE),
    SOURCE("来源", "source", SecurityDataset.FIREWALL_ADAPTIVE),
    ACTION("操作", "action", SecurityDataset.FIREWALL_ADAPTIVE),
    DEVICE("客户端设备类型", "clientDeviceType", SecurityDataset.HTTP_ADAPTIVE),
    HTTP_VERSION("HTTP 版本", "clientRequestHTTPProtocol", SecurityDataset.HTTP_ADAPTIVE),
    CACHE_STATUS("缓存状态", "cacheStatus", SecurityDataset.HTTP_ADAPTIVE)
}

/** 筛选器条件（GraphQL 操作符后缀；无后缀=等于） */
enum class SecurityFilterOp(val label: String, val suffix: String) {
    EQ("等于", ""),
    NEQ("不等于", "_neq"),
    CONTAINS("包含", "_like"),
    NOT_CONTAINS("不包含", "_nlike")
}

/** 单条筛选器 */
data class SecurityFilter(
    val attr: SecurityFilterAttr,
    val op: SecurityFilterOp,
    val value: String
)

/** 概况段（分组=全部：回源/命中/缓解；分组=X：TopN 分组占比） */
data class SecuritySegment(val name: String, val count: Long)

/** 24h 趋势点（按时间粒度） */
data class SecurityTrendPoint(val label: String, val count: Long)

/** 趋势序列（分组=全部：单条"请求"；分组=X：Top5 分组各一条） */
data class SecurityTrendSeries(val name: String, val points: List<SecurityTrendPoint> = emptyList())

/** 分组分布项 */
data class SecurityBreakdownItem(val name: String, val count: Long)

/** 安全日志条目（firewallEventsAdaptive，字段名为 GraphQL camelCase） */
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

/**
 * 安全分析 GraphQL 查询构建与响应解析。
 * schema 结论：
 * - 概况（分组=全部，回源/命中/缓解）用 httpRequests1hGroups：requests(总)、cachedRequests(命中)、threats(缓解)；origin=总-命中-缓解
 * - 概况/趋势（分组=X）与趋势用 httpRequestsAdaptiveGroups（或 firewallEventsAdaptiveGroups 当维度为 action/source）
 * - 日志用 firewallEventsAdaptive（字段 camelCase：clientCountryName/clientRequestHTTPProtocol/clientRequestHTTPHost 等）
 * - filter 操作符：等于(无后缀)/不等于(_neq)/包含(_like)/不包含(_nlike)；时间窗 datetime_geq/leq
 */
object SecurityAnalyticsParser {

    private const val H1_GROUPS = "httpRequests1hGroups"
    private const val HTTP_ADAPTIVE_GROUPS = "httpRequestsAdaptiveGroups"
    private const val FW_ADAPTIVE_GROUPS = "firewallEventsAdaptiveGroups"
    private const val FW_ADAPTIVE = "firewallEventsAdaptive"
    private const val LOG_LIMIT = 200
    private const val TOP_N = 5

    // ===== 查询构建 =====

    /** 概况查询：分组=全部 → 1hGroups(requests/cachedRequests/threats)；分组=X → adaptive 按分组维度 TopN */
    fun overviewQuery(zoneId: String, range: SecurityTimeRange, groupBy: SecurityGroupBy, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        if (groupBy == SecurityGroupBy.ALL) {
            return buildString {
                append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                append(H1_GROUPS).append("(limit: 48, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
                append(" sum { requests cachedRequests threats }\n }\n }\n }\n }")
            }
        }
        val dim = groupBy.dimension!!
        val dataset = if (groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE) FW_ADAPTIVE_GROUPS else HTTP_ADAPTIVE_GROUPS
        val filter = buildFilter(filters, groupBy.dataset)
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(dataset).append("(limit: $TOP_N, orderBy: [count_DESC], filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
            if (filter.isNotEmpty()) append(", $filter")
            append("}) {\n count\n dimensions { $dim }\n }\n }\n }\n }")
        }
    }

    /** 趋势查询：分组=全部 → 单序列(时间粒度)；分组=X → 按[时间粒度,分组维度]取 TopN 分组时序 */
    fun trendQuery(zoneId: String, range: SecurityTimeRange, groupBy: SecurityGroupBy, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        val timeDim = range.trendDimension
        if (groupBy == SecurityGroupBy.ALL) {
            val filter = buildFilter(filters, SecurityDataset.HTTP_ADAPTIVE)
            return buildString {
                append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                append(HTTP_ADAPTIVE_GROUPS).append("(limit: 500, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
                if (filter.isNotEmpty()) append(", $filter")
                append("}) {\n count\n dimensions { $timeDim }\n }\n }\n }\n }")
            }
        }
        val dim = groupBy.dimension!!
        val dataset = if (groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE) FW_ADAPTIVE_GROUPS else HTTP_ADAPTIVE_GROUPS
        val filter = buildFilter(filters, groupBy.dataset)
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(dataset).append("(limit: 2000, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
            if (filter.isNotEmpty()) append(", $filter")
            append("}) {\n count\n dimensions { $timeDim $dim }\n }\n }\n }\n }")
        }
    }

    /** 日志查询：firewallEventsAdaptive 最近 N 条（时间窗 + 全局筛选器） */
    fun logsQuery(zoneId: String, range: SecurityTimeRange, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        val filter = buildFilter(filters, SecurityDataset.FIREWALL_ADAPTIVE)
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(FW_ADAPTIVE).append("(limit: $LOG_LIMIT, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
            if (filter.isNotEmpty()) append(", $filter")
            append("}) {\n")
            append(" datetime action source clientIP clientCountryName clientDeviceType clientRequestHTTPProtocol cacheStatus clientRequestHost rayId\n }\n }\n }\n }")
        }
    }

    /** 构建筛选器 filter 片段（多筛选器 AND 叠加） */
    private fun buildFilter(filters: List<SecurityFilter>, dataset: SecurityDataset): String {
        val parts = filters.filter { it.value.isNotBlank() }.mapNotNull { f ->
            if (!datasetCompatible(f.attr.dataset, dataset)) return@mapNotNull null
            val key = f.attr.field + f.op.suffix
            "$key: \"${escapeValue(f.value)}\""
        }
        return parts.joinToString(", ")
    }

    /** 筛选器属性数据集是否与查询数据集兼容（http 属性在 firewall 也可用，反之不行） */
    private fun datasetCompatible(attrDataset: SecurityDataset, queryDataset: SecurityDataset): Boolean =
        attrDataset == SecurityDataset.HTTP_ADAPTIVE || queryDataset == SecurityDataset.FIREWALL_ADAPTIVE

    private fun escapeValue(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

    // ===== 解析 =====

    /** 解析概况段列表 */
    fun parseOverview(root: JsonElement, groupBy: SecurityGroupBy): List<SecuritySegment> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray ?: return emptyList()
        if (groupBy == SecurityGroupBy.ALL) {
            var requests = 0L; var cached = 0L; var threats = 0L
            (zones.firstOrNull()?.jsonObject?.get(H1_GROUPS) as? JsonArray)?.forEach { g ->
                val s = g.jsonObject["sum"] as? JsonObject ?: return@forEach
                requests += s["requests"]?.jsonPrimitive?.longOrNull ?: 0L
                cached += s["cachedRequests"]?.jsonPrimitive?.longOrNull ?: 0L
                threats += s["threats"]?.jsonPrimitive?.longOrNull ?: 0L
            }
            return listOf(
                SecuritySegment("回源", (requests - cached - threats).coerceAtLeast(0)),
                SecuritySegment("命中", cached),
                SecuritySegment("缓解", threats)
            )
        }
        val dataset = if (groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE) FW_ADAPTIVE_GROUPS else HTTP_ADAPTIVE_GROUPS
        return parseTopBreakdown(zones.firstOrNull()?.jsonObject?.get(dataset) as? JsonArray, TOP_N)
    }

    /** 解析趋势序列列表（分组=全部单序列；分组=X Top5 分组时序） */
    fun parseTrend(root: JsonElement, range: SecurityTimeRange, groupBy: SecurityGroupBy): List<SecurityTrendSeries> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray ?: return emptyList()
        if (groupBy == SecurityGroupBy.ALL) {
            val groups = zones.firstOrNull()?.jsonObject?.get(HTTP_ADAPTIVE_GROUPS) as? JsonArray ?: return emptyList()
            val byLabel = LinkedHashMap<String, Long>()
            groups.forEach { g ->
                val o = g.jsonObject
                val label = o["dimensions"]?.jsonObject?.get(range.trendDimension)?.jsonPrimitive?.content
                    ?.let { formatTrendLabel(it, range) } ?: return@forEach
                byLabel.merge(label, o["count"]?.jsonPrimitive?.longOrNull ?: 0L) { a, b -> a + b }
            }
            return listOf(SecurityTrendSeries("请求", byLabel.map { SecurityTrendPoint(it.key, it.value) }.sortedBy { it.label }))
        }
        val dim = groupBy.dimension!!
        val dataset = if (groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE) FW_ADAPTIVE_GROUPS else HTTP_ADAPTIVE_GROUPS
        val groups = zones.firstOrNull()?.jsonObject?.get(dataset) as? JsonArray ?: return emptyList()
        val perGroup = LinkedHashMap<String, LinkedHashMap<String, Long>>()
        groups.forEach { g ->
            val o = g.jsonObject
            val dims = o["dimensions"]?.jsonObject ?: return@forEach
            val name = dims[dim]?.jsonPrimitive?.content ?: return@forEach
            if (name.isBlank()) return@forEach
            val label = dims[range.trendDimension]?.jsonPrimitive?.content?.let { formatTrendLabel(it, range) } ?: return@forEach
            val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
            perGroup.getOrPut(name) { LinkedHashMap() }.merge(label, count) { a, b -> a + b }
        }
        return perGroup.entries
            .map { (name, m) -> SecurityTrendSeries(name, m.map { SecurityTrendPoint(it.key, it.value) }.sortedBy { it.label }) }
            .sortedByDescending { it.points.sumOf { p -> p.count } }
            .take(TOP_N)
            .filter { it.points.isNotEmpty() }
    }

    private fun parseTopBreakdown(groups: JsonArray?, topN: Int): List<SecuritySegment> {
        groups ?: return emptyList()
        val items = mutableListOf<SecuritySegment>()
        groups.forEach { g ->
            val o = g.jsonObject
            val dims = o["dimensions"]?.jsonObject ?: return@forEach
            val name = dims.values.firstOrNull()?.jsonPrimitive?.content ?: return@forEach
            if (name.isBlank()) return@forEach
            items.add(SecuritySegment(name, o["count"]?.jsonPrimitive?.longOrNull ?: 0L))
        }
        return items.sortedByDescending { it.count }.take(topN)
    }

    /** 解析安全日志列表 */
    fun parseLogs(root: JsonElement): List<SecurityLogEntry> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray ?: return emptyList()
        val events = zones.firstOrNull()?.jsonObject?.get(FW_ADAPTIVE) as? JsonArray ?: return emptyList()
        return events.mapNotNull { e ->
            val o = e.jsonObject
            SecurityLogEntry(
                datetime = o["datetime"]?.jsonPrimitive?.content?.let(::formatLogTime) ?: "",
                action = o["action"]?.jsonPrimitive?.content ?: "",
                source = o["source"]?.jsonPrimitive?.content,
                clientIP = o["clientIP"]?.jsonPrimitive?.content,
                clientCountry = o["clientCountryName"]?.jsonPrimitive?.content,
                deviceType = o["clientDeviceType"]?.jsonPrimitive?.content,
                httpVersion = o["clientRequestHTTPProtocol"]?.jsonPrimitive?.content,
                cacheStatus = o["cacheStatus"]?.jsonPrimitive?.content,
                host = o["clientRequestHost"]?.jsonPrimitive?.content
            )
        }
    }

    // ===== 工具 =====

    private fun window(range: SecurityTimeRange): Pair<String, String> {
        val now = System.currentTimeMillis()
        return isoUtc(now - range.millis) to isoUtc(now)
    }

    private fun isoUtc(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ts))

    /** 趋势时间标签：5/15 分钟→HH:mm；小时→HH时 */
    private fun formatTrendLabel(iso: String, range: SecurityTimeRange): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso) ?: return iso
        val out = if (range == SecurityTimeRange.H12 || range == SecurityTimeRange.H24) "HH时" else "HH:mm"
        SimpleDateFormat(out, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsed)
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