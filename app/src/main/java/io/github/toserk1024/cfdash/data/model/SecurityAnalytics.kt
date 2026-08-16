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

    val trendDimension: String
        get() = when (this) {
            HALF_HOUR -> "datetimeFiveMinutes"
            H3 -> "datetimeFifteenMinutes"
            H12, H24 -> "datetimeHour"
        }
}

/** 分组数据源数据集 */
enum class SecurityDataset { HTTP_ADAPTIVE, FIREWALL_ADAPTIVE }

/** 安全分组视图维度 */
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

/** 单条筛选器（values 多值：仅"包含"支持多项→GraphQL _in；单值条件 values 大小为 1） */
data class SecurityFilter(
    val attr: SecurityFilterAttr,
    val op: SecurityFilterOp,
    val values: List<String>
) {
    val value: String get() = values.firstOrNull() ?: ""
}

/** 概况段（分组=全部：回源/命中/缓解；分组=X：TopN 分组占比） */
data class SecuritySegment(val name: String, val count: Long)

/** 趋势点（按时间粒度） */
data class SecurityTrendPoint(val label: String, val count: Long)

/** 趋势序列（分组=全部：请求/回源/命中/缓解 四条；分组=X：Top5 分组各一条） */
data class SecurityTrendSeries(val name: String, val points: List<SecurityTrendPoint> = emptyList())

/** 日志可用列（用户可自选显示，持久化） */
enum class SecurityLogColumn(val label: String, val field: String) {
    ACTION("采取的措施", "action"),
    ASN("ASN", "clientAsn"),
    COUNTRY("国家/地区", "clientCountryName"),
    IP("IP 地址", "clientIP"),
    HOST("主机", "clientRequestHost"),
    METHOD("方法", "clientRequestMethod"),
    HTTP_VERSION("HTTP 版本", "clientRequestHTTPProtocol"),
    PATH("路径", "clientRequestPath"),
    QUERY("查询字符串", "clientRequestQuery"),
    RAY_ID("Ray ID", "rayId"),
    RULE_ID("规则 ID", "ruleId"),
    SERVICE("服务", "source"),
    USER_AGENT("用户代理", "clientRequestHTTPUserAgent");

    companion object {
        /** 默认显示列 */
        val DEFAULT = setOf(ACTION, COUNTRY, IP, HOST, HTTP_VERSION)
        fun byName(name: String) = entries.firstOrNull { it.name == name }
    }
}

/** 安全日志条目（firewallEventsAdaptive，字段名为 GraphQL camelCase） */
data class SecurityLogEntry(
    val datetime: String,
    val action: String,
    val source: String?,
    val clientIP: String?,
    val clientCountry: String?,
    val clientAsn: String?,
    val method: String?,
    val httpVersion: String?,
    val path: String?,
    val query: String?,
    val rayId: String?,
    val ruleId: String?,
    val userAgent: String?,
    val host: String?
)

/** 国家代码 ↔ 中文名映射（筛选器国家属性用） */
object CountryMapping {
    private val codeToName = mapOf(
        "CN" to "中国", "HK" to "中国香港", "MO" to "中国澳门", "TW" to "中国台湾",
        "US" to "美国", "JP" to "日本", "KR" to "韩国", "SG" to "新加坡",
        "GB" to "英国", "DE" to "德国", "FR" to "法国", "RU" to "俄罗斯",
        "CA" to "加拿大", "AU" to "澳大利亚", "IN" to "印度", "BR" to "巴西",
        "NL" to "荷兰", "SE" to "瑞典", "CH" to "瑞士", "IT" to "意大利",
        "ES" to "西班牙", "UA" to "乌克兰", "PL" to "波兰", "TR" to "土耳其",
        "ID" to "印度尼西亚", "MY" to "马来西亚", "TH" to "泰国", "VN" to "越南",
        "PH" to "菲律宾", "NZ" to "新西兰", "IE" to "爱尔兰", "IL" to "以色列",
        "SA" to "沙特阿拉伯", "AE" to "阿联酋", "ZA" to "南非", "EG" to "埃及",
        "MX" to "墨西哥", "AR" to "阿根廷", "CL" to "智利", "CO" to "哥伦比亚",
        "FI" to "芬兰", "NO" to "挪威", "DK" to "丹麦", "BE" to "比利时",
        "AT" to "奥地利", "PT" to "葡萄牙", "GR" to "希腊", "CZ" to "捷克"
    )
    private val nameToCode = codeToName.entries.associate { (c, n) -> n to c }

    fun codeToName(code: String): String = codeToName[code] ?: code
    fun nameToCode(name: String): String? = nameToCode[name]
    fun search(query: String, limit: Int = 20): List<Pair<String, String>> {
        if (query.isBlank()) return codeToName.entries.take(limit).map { it.key to it.value }
        val q = query.trim()
        return codeToName.entries
            .filter { it.value.contains(q, ignoreCase = true) || it.key.contains(q, ignoreCase = true) }
            .take(limit).map { it.key to it.value }
    }
}

/** HTTP 版本 / 缓存状态 / 操作 等候选值（筛选器选择框用） */
object SecurityCandidates {
    val HTTP_VERSIONS = listOf("HTTP/1.1", "HTTP/2", "HTTP/3")
    val CACHE_STATUS = listOf("hit", "miss", "dynamic", "expired", "bypass", "unknown")
    val ACTIONS = listOf("allow", "block", "challenge", "jschallenge", "managedchallenge", "log", "connectionclose", "bypass")
    val DEVICES = listOf("Desktop", "Mobile", "Tablet", "Other")
}

/**
 * 安全分析 GraphQL 查询构建与响应解析。
 * - 概况/趋势（分组=全部）用 httpRequests1hGroups：requests/cachedRequests(命中)/threats(缓解)；origin=总-命中-缓解
 * - 分组=X（非 action/source）用 httpRequestsAdaptiveGroups
 * - 分组=action/source 用 firewallEventsAdaptive（events，客户端聚合，因 Groups 对当前 zone 受限）
 * - 日志用 firewallEventsAdaptive；filter：等于/不等于(_neq)/包含(_like)/不包含(_nlike)；多值"包含"→_in
 */
object SecurityAnalyticsParser {

    private const val H1_GROUPS = "httpRequests1hGroups"
    private const val HTTP_ADAPTIVE_GROUPS = "httpRequestsAdaptiveGroups"
    private const val FW_ADAPTIVE = "firewallEventsAdaptive"
    private const val LOG_LIMIT = 200
    private const val EVENTS_LIMIT = 1000
    private const val TOP_N = 5

    // ===== 查询构建 =====

    fun overviewQuery(zoneId: String, range: SecurityTimeRange, groupBy: SecurityGroupBy, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        return when {
            groupBy == SecurityGroupBy.ALL -> buildString {
                append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                append(H1_GROUPS).append("(limit: 48, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
                append(" sum { requests cachedRequests threats }\n }\n }\n }\n }")
            }
            groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE -> {
                // events 客户端聚合（source/action）
                val dim = groupBy.dimension!!
                val filter = buildFilter(filters, SecurityDataset.FIREWALL_ADAPTIVE)
                buildString {
                    append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                    append(FW_ADAPTIVE).append("(limit: $EVENTS_LIMIT, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
                    if (filter.isNotEmpty()) append(", $filter")
                    append("}) {\n $dim\n }\n }\n }\n }")
                }
            }
            else -> {
                val dim = groupBy.dimension!!
                val filter = buildFilter(filters, groupBy.dataset)
                buildString {
                    append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                    append(HTTP_ADAPTIVE_GROUPS).append("(limit: $TOP_N, orderBy: [count_DESC], filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
                    if (filter.isNotEmpty()) append(", $filter")
                    append("}) {\n count\n dimensions { $dim }\n }\n }\n }\n }")
                }
            }
        }
    }

    fun trendQuery(zoneId: String, range: SecurityTimeRange, groupBy: SecurityGroupBy, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        return when {
            groupBy == SecurityGroupBy.ALL -> buildString {
                append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                append(H1_GROUPS).append("(limit: 48, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"}) {\n")
                append(" dimensions { datetime }\n sum { requests cachedRequests threats }\n }\n }\n }\n }")
            }
            groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE -> {
                val dim = groupBy.dimension!!
                val filter = buildFilter(filters, SecurityDataset.FIREWALL_ADAPTIVE)
                buildString {
                    append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                    append(FW_ADAPTIVE).append("(limit: ${EVENTS_LIMIT * 2}, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
                    if (filter.isNotEmpty()) append(", $filter")
                    append("}) {\n datetime $dim\n }\n }\n }\n }")
                }
            }
            else -> {
                val dim = groupBy.dimension!!
                val timeDim = range.trendDimension
                val filter = buildFilter(filters, groupBy.dataset)
                buildString {
                    append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
                    append(HTTP_ADAPTIVE_GROUPS).append("(limit: 2000, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
                    if (filter.isNotEmpty()) append(", $filter")
                    append("}) {\n count\n dimensions { $timeDim $dim }\n }\n }\n }\n }")
                }
            }
        }
    }

    /** 日志查询：拉取全部可选列字段，客户端按选中列显示 */
    fun logsQuery(zoneId: String, range: SecurityTimeRange, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        val filter = buildFilter(filters, SecurityDataset.FIREWALL_ADAPTIVE)
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(FW_ADAPTIVE).append("(limit: $LOG_LIMIT, filter: {datetime_geq: \"$s\", datetime_leq: \"$e\"")
            if (filter.isNotEmpty()) append(", $filter")
            append("}) {\n")
            append(" datetime action source clientIP clientCountryName clientAsn clientRequestHTTPProtocol clientRequestHost clientRequestMethod clientRequestPath clientRequestQuery clientRequestHTTPUserAgent rayId ruleId\n }\n }\n }\n }")
        }
    }

    /** 构建筛选器 filter 片段：多值"包含"→_in；单值→字段+操作符后缀 */
    private fun buildFilter(filters: List<SecurityFilter>, dataset: SecurityDataset): String {
        val parts = filters.mapNotNull { f ->
            if (!datasetCompatible(f.attr.dataset, dataset)) return@mapNotNull null
            val vals = f.values.filter { it.isNotBlank() }
            if (vals.isEmpty()) return@mapNotNull null
            if (vals.size > 1) {
                "${f.attr.field}_in: [${vals.joinToString(", ") { "\"${escapeValue(it)}\"" }}]"
            } else {
                "${f.attr.field}${f.op.suffix}: \"${escapeValue(vals[0])}\""
            }
        }
        return parts.joinToString(", ")
    }

    private fun datasetCompatible(attrDataset: SecurityDataset, queryDataset: SecurityDataset): Boolean =
        attrDataset == SecurityDataset.HTTP_ADAPTIVE || queryDataset == SecurityDataset.FIREWALL_ADAPTIVE

    private fun escapeValue(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

    // ===== 解析 =====

    fun parseOverview(root: JsonElement, groupBy: SecurityGroupBy): List<SecuritySegment> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray ?: return emptyList()
        return when {
            groupBy == SecurityGroupBy.ALL -> {
                var requests = 0L; var cached = 0L; var threats = 0L
                (zones.firstOrNull()?.jsonObject?.get(H1_GROUPS) as? JsonArray)?.forEach { g ->
                    val s = g.jsonObject["sum"] as? JsonObject ?: return@forEach
                    requests += s["requests"]?.jsonPrimitive?.longOrNull ?: 0L
                    cached += s["cachedRequests"]?.jsonPrimitive?.longOrNull ?: 0L
                    threats += s["threats"]?.jsonPrimitive?.longOrNull ?: 0L
                }
                listOf(
                    SecuritySegment("回源", (requests - cached - threats).coerceAtLeast(0)),
                    SecuritySegment("命中", cached),
                    SecuritySegment("缓解", threats)
                )
            }
            groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE -> {
                val dim = groupBy.dimension!!
                val counts = LinkedHashMap<String, Long>()
                (zones.firstOrNull()?.jsonObject?.get(FW_ADAPTIVE) as? JsonArray)?.forEach { e ->
                    val name = e.jsonObject[dim]?.jsonPrimitive?.content ?: return@forEach
                    if (name.isNotBlank()) counts[name] = (counts[name] ?: 0L) + 1
                }
                counts.entries.sortedByDescending { it.value }.take(TOP_N).map { SecuritySegment(it.key, it.value) }
            }
            else -> {
                val groups = zones.firstOrNull()?.jsonObject?.get(HTTP_ADAPTIVE_GROUPS) as? JsonArray ?: return emptyList()
                parseTopBreakdown(groups, TOP_N)
            }
        }
    }

    fun parseTrend(root: JsonElement, range: SecurityTimeRange, groupBy: SecurityGroupBy): List<SecurityTrendSeries> {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray ?: return emptyList()
        return when {
            groupBy == SecurityGroupBy.ALL -> {
                // 请求/回源/命中/缓解 四条线
                val groups = zones.firstOrNull()?.jsonObject?.get(H1_GROUPS) as? JsonArray ?: return emptyList()
                val req = LinkedHashMap<String, Long>()
                val cached = LinkedHashMap<String, Long>()
                val threats = LinkedHashMap<String, Long>()
                groups.forEach { g ->
                    val o = g.jsonObject
                    val s = o["sum"] as? JsonObject ?: return@forEach
                    val label = o["dimensions"]?.jsonObject?.get("datetime")?.jsonPrimitive?.content?.let { formatTrendLabel(it, range) } ?: return@forEach
                    val r = s["requests"]?.jsonPrimitive?.longOrNull ?: 0L
                    val c = s["cachedRequests"]?.jsonPrimitive?.longOrNull ?: 0L
                    val t = s["threats"]?.jsonPrimitive?.longOrNull ?: 0L
                    req.merge(label, r) { a, b -> a + b }
                    cached.merge(label, c) { a, b -> a + b }
                    threats.merge(label, t) { a, b -> a + b }
                }
                val labels = (req.keys + cached.keys + threats.keys).sorted()
                fun series(name: String, f: (String) -> Long) = SecurityTrendSeries(name, labels.map { SecurityTrendPoint(it, f(it)) })
                listOf(
                    series("请求") { req[it] ?: 0L },
                    series("回源") { (req[it] ?: 0L) - (cached[it] ?: 0L) - (threats[it] ?: 0L) },
                    series("命中") { cached[it] ?: 0L },
                    series("缓解") { threats[it] ?: 0L }
                )
            }
            groupBy.dataset == SecurityDataset.FIREWALL_ADAPTIVE -> {
                val dim = groupBy.dimension!!
                val perGroup = LinkedHashMap<String, LinkedHashMap<String, Long>>()
                (zones.firstOrNull()?.jsonObject?.get(FW_ADAPTIVE) as? JsonArray)?.forEach { e ->
                    val o = e.jsonObject
                    val name = o[dim]?.jsonPrimitive?.content ?: return@forEach
                    if (name.isBlank()) return@forEach
                    val label = o["datetime"]?.jsonPrimitive?.content?.let { formatTrendLabel(it, range) } ?: return@forEach
                    perGroup.getOrPut(name) { LinkedHashMap() }.merge(label, 1L) { a, b -> a + b }
                }
                perGroup.entries
                    .map { (name, m) -> SecurityTrendSeries(name, m.map { SecurityTrendPoint(it.key, it.value) }.sortedBy { it.label }) }
                    .sortedByDescending { it.points.sumOf { p -> p.count } }
                    .take(TOP_N)
                    .filter { it.points.isNotEmpty() }
            }
            else -> {
                val dim = groupBy.dimension!!
                val groups = zones.firstOrNull()?.jsonObject?.get(HTTP_ADAPTIVE_GROUPS) as? JsonArray ?: return emptyList()
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
                perGroup.entries
                    .map { (name, m) -> SecurityTrendSeries(name, m.map { SecurityTrendPoint(it.key, it.value) }.sortedBy { it.label }) }
                    .sortedByDescending { it.points.sumOf { p -> p.count } }
                    .take(TOP_N)
                    .filter { it.points.isNotEmpty() }
            }
        }
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
                clientAsn = o["clientAsn"]?.jsonPrimitive?.content,
                method = o["clientRequestMethod"]?.jsonPrimitive?.content,
                httpVersion = o["clientRequestHTTPProtocol"]?.jsonPrimitive?.content,
                path = o["clientRequestPath"]?.jsonPrimitive?.content,
                query = o["clientRequestQuery"]?.jsonPrimitive?.content,
                rayId = o["rayId"]?.jsonPrimitive?.content,
                ruleId = o["ruleId"]?.jsonPrimitive?.content,
                userAgent = o["clientRequestHTTPUserAgent"]?.jsonPrimitive?.content,
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

    private fun formatTrendLabel(iso: String, range: SecurityTimeRange): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso) ?: return iso
        val out = if (range == SecurityTimeRange.H12 || range == SecurityTimeRange.H24) "HH时" else "HH:mm"
        SimpleDateFormat(out, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(parsed)
    } catch (e: Exception) {
        iso
    }

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