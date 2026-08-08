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

/** 统计时间范围（数据集 + limit 映射，limit 留缓冲防边界漏行） */
enum class AnalyticsRange(val label: String, val dataset: String, val limit: Int) {
    H24("24小时", "httpRequests1hGroups", 48),
    D7("7天", "httpRequests1dGroups", 15),
    D30("30天", "httpRequests1dGroups", 32)
}

/** 统计聚合结果（GraphQL sum + uniq） */
@Serializable
data class AnalyticsSum(
    val requests: Long = 0,
    val threats: Long = 0,
    val bytes: Long = 0,
    val cachedRequests: Long = 0,
    val cachedBytes: Long = 0,
    val uniques: Long = 0
) {
    /** 缓存命中率 0~1 */
    val cacheHitRatio: Float get() = if (requests > 0) cachedRequests.toFloat() / requests else 0f

    operator fun plus(other: AnalyticsSum) = AnalyticsSum(
        requests + other.requests,
        threats + other.threats,
        bytes + other.bytes,
        cachedRequests + other.cachedRequests,
        cachedBytes + other.cachedBytes,
        uniques + other.uniques
    )
}

/** 时间序列点（趋势图，label 为格式化后的时间标签） */
data class AnalyticsSeriesPoint(
    val label: String,
    val requests: Long,
    val threats: Long,
    val bytes: Long,
    val cachedRequests: Long,
    val cachedBytes: Long,
    val uniques: Long
)

/** 时间序列（趋势图数据） */
data class AnalyticsSeries(
    val points: List<AnalyticsSeriesPoint> = emptyList()
) {
    val isEmpty: Boolean get() = points.isEmpty()
}

/** 维度分布项（国家/状态码/缓存状态等） */
data class AnalyticsBreakdown(
    val name: String,
    val value: Long
)

/** 账号级域名拆分项（各域名请求量） */
data class ZoneAnalyticsItem(
    val zoneId: String,
    val zoneName: String,
    val sum: AnalyticsSum
)

/** 维度分布类型（GraphQL groups 的 dimensions 分组字段，配合 orderBy: [count_DESC] 取 Top N） */
enum class BreakdownDimension(val field: String) {
    COUNTRY("clientCountryName"),
    STATUS("edgeResponseStatus"),
    CACHE("cacheStatus")
}

/** GraphQL Analytics 查询构建与响应解析（GraphQL 响应为 data/errors 结构，非 ApiResponse 包装） */
object AnalyticsParser {

    // ===== 汇总查询（原有，仅做总量累加）=====

    /** 构建域名级统计查询（不排序：只做总量累加，orderBy 对 Groups 数据集仅支持聚合字段，规避排序错误） */
    fun zoneQuery(zoneId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range))
        append("}) {\n sum { requests threats bytes cachedRequests cachedBytes }\n }\n }\n }\n }")
    }

    /** 构建账号级统计查询（遍历账号下所有域名） */
    fun accountQuery(accountId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n accounts(filter: {accountTag: \"$accountId\"}) {\n zones {\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range))
        append("}) {\n sum { requests threats bytes cachedRequests cachedBytes }\n }\n }\n }\n }\n }")
    }

    // ===== 趋势查询（时间序列：dimensions + sum + uniq）=====

    /** 构建域名级趋势查询（返回按小时/天分组的时间序列） */
    fun zoneSeriesQuery(zoneId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range))
        append("}) {\n dimensions { ").append(seriesDimensionField(range))
        append(" }\n sum { requests threats bytes cachedRequests cachedBytes }\n uniq { uniques }\n }\n }\n }\n }")
    }

    /** 构建账号级趋势查询（各域名的时间序列在客户端按时间点合并累加） */
    fun accountSeriesQuery(accountId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n accounts(filter: {accountTag: \"$accountId\"}) {\n zones {\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range))
        append("}) {\n dimensions { ").append(seriesDimensionField(range))
        append(" }\n sum { requests threats bytes cachedRequests cachedBytes }\n uniq { uniques }\n }\n }\n }\n }\n }")
    }

    // ===== 维度分布查询（count + dimensions，orderBy 聚合字段取 Top N）=====

    /** 构建域名级维度分布查询 */
    fun zoneBreakdownQuery(zoneId: String, range: AnalyticsRange, dimension: BreakdownDimension): String = buildString {
        append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
        append(range.dataset)
        append("(limit: 15, filter: {").append(filterField(range))
        append("}, orderBy: [count_DESC]) {\n count\n dimensions { ").append(dimension.field)
        append(" }\n }\n }\n }\n }")
    }

    /** 构建账号级维度分布查询（各域名的分布合并累加） */
    fun accountBreakdownQuery(accountId: String, range: AnalyticsRange, dimension: BreakdownDimension): String = buildString {
        append("query {\n viewer {\n accounts(filter: {accountTag: \"$accountId\"}) {\n zones {\n ")
        append(range.dataset)
        append("(limit: 15, filter: {").append(filterField(range))
        append("}, orderBy: [count_DESC]) {\n count\n dimensions { ").append(dimension.field)
        append(" }\n }\n }\n }\n }\n }")
    }

    // ===== 账号级域名拆分查询（zones 节点带 zoneTag/name + 各自 sum）=====

    /** 构建账号级域名拆分查询（各域名汇总数据） */
    fun accountZoneBreakdownQuery(accountId: String, range: AnalyticsRange): String = buildString {
        append("query {\n viewer {\n accounts(filter: {accountTag: \"$accountId\"}) {\n zones {\n zoneTag\n name\n ")
        append(range.dataset)
        append("(limit: ").append(range.limit)
        append(", filter: {").append(filterField(range))
        append("}) {\n sum { requests threats bytes cachedRequests cachedBytes }\n }\n }\n }\n }\n }")
    }

    // ===== 汇总解析（原有）=====

    /** 解析域名级统计：data.viewer.zones[0][dataset][] 累加 */
    fun parseZone(root: JsonElement, range: AnalyticsRange): AnalyticsSum {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return AnalyticsSum()
        return zones.firstOrNull()?.let { accumulate(it.jsonObject, range.dataset) } ?: AnalyticsSum()
    }

    /** 解析账号级统计：data.viewer.accounts[0].zones[] 各域名累加 */
    fun parseAccount(root: JsonElement, range: AnalyticsRange): AnalyticsSum {
        val zones = accountZones(root) ?: return AnalyticsSum()
        return zones.fold(AnalyticsSum()) { acc, zone -> acc + accumulate(zone.jsonObject, range.dataset) }
    }

    // ===== 趋势解析 =====

    /** 解析域名级趋势（单域名的时间序列） */
    fun parseZoneSeries(root: JsonElement, range: AnalyticsRange): AnalyticsSeries {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return AnalyticsSeries()
        val groups = zones.firstOrNull()?.jsonObject?.get(range.dataset) as? JsonArray ?: return AnalyticsSeries()
        return AnalyticsSeries(
            groups.mapNotNull { seriesPoint(it.jsonObject, range) }.sortedBy { it.label }
        )
    }

    /** 解析账号级趋势（各域名按时间点合并累加） */
    fun parseAccountSeries(root: JsonElement, range: AnalyticsRange): AnalyticsSeries {
        val zones = accountZones(root) ?: return AnalyticsSeries()
        val byLabel = LinkedHashMap<String, AnalyticsSeriesPoint>()
        zones.forEach { zone ->
            (zone[range.dataset] as? JsonArray)?.forEach { g ->
                val point = seriesPoint(g.jsonObject, range) ?: return@forEach
                byLabel.merge(point.label, point) { a, b ->
                    AnalyticsSeriesPoint(
                        label = a.label,
                        requests = a.requests + b.requests,
                        threats = a.threats + b.threats,
                        bytes = a.bytes + b.bytes,
                        cachedRequests = a.cachedRequests + b.cachedRequests,
                        cachedBytes = a.cachedBytes + b.cachedBytes,
                        uniques = a.uniques + b.uniques
                    )
                }
            }
        }
        return AnalyticsSeries(byLabel.values.sortedBy { it.label })
    }

    // ===== 维度分布解析 =====

    /** 解析域名级维度分布 */
    fun parseZoneBreakdown(root: JsonElement, range: AnalyticsRange, dimension: BreakdownDimension): List<AnalyticsBreakdown> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
            ?: return emptyList()
        return parseGroups(zones.firstOrNull()?.jsonObject?.get(range.dataset) as? JsonArray, dimension)
    }

    /** 解析账号级维度分布（各域名同名维度合并累加） */
    fun parseAccountBreakdown(root: JsonElement, range: AnalyticsRange, dimension: BreakdownDimension): List<AnalyticsBreakdown> {
        val zones = accountZones(root) ?: return emptyList()
        val map = LinkedHashMap<String, Long>()
        zones.forEach { zone ->
            (zone[range.dataset] as? JsonArray)?.forEach { g ->
                val name = dimensionName(g.jsonObject, dimension) ?: return@forEach
                map[name] = (map[name] ?: 0L) + (g.jsonObject["count"]?.jsonPrimitive?.longOrNull ?: 0L)
            }
        }
        return map.entries.sortedByDescending { it.value }.map { AnalyticsBreakdown(it.key, it.value) }
    }

    // ===== 账号级域名拆分解析 =====

    /** 解析账号级域名拆分（按请求量降序，过滤零流量域名） */
    fun parseZoneItems(root: JsonElement, range: AnalyticsRange): List<ZoneAnalyticsItem> {
        val zones = accountZones(root) ?: return emptyList()
        return zones.mapNotNull { zone ->
            val zoneId = zone["zoneTag"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val zoneName = zone["name"]?.jsonPrimitive?.content ?: zoneId
            ZoneAnalyticsItem(zoneId, zoneName, accumulate(zone, range.dataset))
        }.filter { it.sum.requests > 0 }.sortedByDescending { it.sum.requests }
    }

    // ===== 内部工具 =====

    /** 取 account 下的 zones 数组 */
    private fun accountZones(root: JsonElement): JsonArray? =
        root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("accounts")
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("zones") as? JsonArray

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

    /** 提取单个序列点（dimensions 时间字段 + sum + uniq） */
    private fun seriesPoint(g: JsonObject, range: AnalyticsRange): AnalyticsSeriesPoint? {
        val dims = g["dimensions"] as? JsonObject ?: return null
        val time = dims[if (range == AnalyticsRange.H24) "datetimeHour" else "date"]?.jsonPrimitive?.content ?: return null
        val s = g["sum"] as? JsonObject
        val u = g["uniq"] as? JsonObject
        return AnalyticsSeriesPoint(
            label = formatSeriesLabel(time, range),
            requests = s?.get("requests")?.jsonPrimitive?.longOrNull ?: 0,
            threats = s?.get("threats")?.jsonPrimitive?.longOrNull ?: 0,
            bytes = s?.get("bytes")?.jsonPrimitive?.longOrNull ?: 0,
            cachedRequests = s?.get("cachedRequests")?.jsonPrimitive?.longOrNull ?: 0,
            cachedBytes = s?.get("cachedBytes")?.jsonPrimitive?.longOrNull ?: 0,
            uniques = u?.get("uniques")?.jsonPrimitive?.longOrNull ?: 0
        )
    }

    /** 解析维度分组数组（count + dimensions[field]），按值降序 */
    private fun parseGroups(groups: JsonArray?, dimension: BreakdownDimension): List<AnalyticsBreakdown> {
        if (groups == null) return emptyList()
        return groups.mapNotNull { g ->
            val name = dimensionName(g.jsonObject, dimension) ?: return@mapNotNull null
            AnalyticsBreakdown(name, g.jsonObject["count"]?.jsonPrimitive?.longOrNull ?: 0L)
        }.sortedByDescending { it.value }
    }

    /** 提取维度名（空值统一为"未知"，状态码保留原值，缓存状态映射中文） */
    private fun dimensionName(g: JsonObject, dimension: BreakdownDimension): String? {
        val dims = g["dimensions"] as? JsonObject ?: return null
        val v = dims[dimension.field]?.jsonPrimitive ?: return null
        val raw = v.content
        if (raw.isBlank()) return "未知"
        return when (dimension) {
            BreakdownDimension.COUNTRY -> raw
            BreakdownDimension.STATUS -> raw
            BreakdownDimension.CACHE -> cacheStatusLabels[raw.lowercase()] ?: raw
        }
    }

    /** cacheStatus 英文值 → 中文（官方值：hit/miss/dynamic/expired/bypass/revalidated/stale 等） */
    private val cacheStatusLabels = mapOf(
        "hit" to "命中",
        "miss" to "未命中",
        "dynamic" to "动态",
        "expired" to "过期",
        "bypass" to "绕过",
        "revalidated" to "重新验证",
        "stale" to "过期",
        "unknown" to "未知"
    )

    /** 趋势时间字段：24h 用 datetimeHour，7d/30d 用 date */
    private fun seriesDimensionField(range: AnalyticsRange): String =
        if (range == AnalyticsRange.H24) "datetimeHour" else "date"

    /** 时间窗过滤字段：24h → datetime_geq/leq；7d/30d → date_geq/leq（UTC） */
    private fun filterField(range: AnalyticsRange): String {
        val (start, end) = rangeWindow(range)
        return if (range == AnalyticsRange.H24) {
            "datetime_geq: \"$start\", datetime_leq: \"$end\""
        } else {
            "date_geq: \"$start\", date_leq: \"$end\""
        }
    }

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

    /** 序列点时间标签：24h → "MM-dd HH时"；7d/30d → "MM-dd"（UTC，避免时区混淆） */
    private fun formatSeriesLabel(iso: String, range: AnalyticsRange): String {
        return try {
            val inPattern = if (range == AnalyticsRange.H24) "yyyy-MM-dd'T'HH:mm:ss'Z'" else "yyyy-MM-dd"
            val parsed = SimpleDateFormat(inPattern, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso) ?: return iso
            val outPattern = if (range == AnalyticsRange.H24) "MM-dd HH时" else "MM-dd"
            SimpleDateFormat(outPattern, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(parsed)
        } catch (e: Exception) {
            iso
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
}