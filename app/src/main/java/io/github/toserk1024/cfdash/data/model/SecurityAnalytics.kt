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

/** 安全页时间范围（与 Cloudflare 官网一致，8 档） */
enum class SecurityTimeRange(val label: String, val millis: Long, val timeDimension: String) {
    M30("30 分钟", 30L * 60_000L, "datetimeMinute"),
    H6("6 小时", 6L * 3_600_000L, "datetimeFiveMinutes"),
    H12("12 小时", 12L * 3_600_000L, "datetimeFiveMinutes"),
    H24("24 小时", 24L * 3_600_000L, "datetimeHour"),
    H72("72 小时", 72L * 3_600_000L, "datetimeHour"),
    D7("7 天", 7L * 86_400_000L, "date"),
    D14("14 天", 14L * 86_400_000L, "date"),
    D31("31 天", 31L * 86_400_000L, "date");

    /** date 维度返回 yyyy-MM-dd，其余返回 ISO datetime */
    val isDayBucket: Boolean get() = timeDimension == "date"
}

/** 趋势/概况的分组维度（全部字段均实测存在于 httpRequestsAdaptiveGroups.dimensions） */
enum class SecurityGroupBy(val label: String, val dimension: String?) {
    ALL("全部", null),
    COUNTRY("国家", "clientCountryName"),
    BROWSER("来源浏览器", "userAgentBrowser"),
    OS("来源操作系统", "userAgentOS"),
    DEVICE("客户端设备类型", "clientDeviceType"),
    IP("来源 IP", "clientIP"),
    HTTP_VERSION("HTTP 版本", "clientRequestHTTPProtocol"),
    CACHE_STATUS("缓存状态", "cacheStatus"),
    ACTION("安全性操作", "securityAction"),
    SOURCE("安全性来源", "securitySource")
}

/** 筛选取值控件类型 */
enum class FilterValueKind { TEXT, COUNTRY, CHOICE }

/** 筛选属性（字段名均经 schema 实测确认） */
enum class SecurityFilterAttr(
    val label: String,
    val field: String,
    val valueKind: FilterValueKind,
    val candidates: List<String> = emptyList(),
    /** 文本型才允许“包含/不包含”（服务端 _like 需 % 通配） */
    val allowContains: Boolean = false
) {
    IP("来源 IP", "clientIP", FilterValueKind.TEXT, allowContains = true),
    COUNTRY("国家", "clientCountryName", FilterValueKind.COUNTRY),
    DEVICE("客户端设备类型", "clientDeviceType", FilterValueKind.CHOICE,
        listOf("desktop", "mobile", "tablet")),
    BROWSER("来源浏览器", "userAgentBrowser", FilterValueKind.CHOICE,
        listOf("Chrome", "ChromeMobile", "Safari", "MobileSafari", "Edge", "Firefox", "Opera", "Curl", "Unknown")),
    OS("来源操作系统", "userAgentOS", FilterValueKind.CHOICE,
        listOf("Windows", "Linux", "MacOSX", "Android", "iOS", "ChromeOS", "Unknown")),
    HTTP_VERSION("HTTP 版本", "clientRequestHTTPProtocol", FilterValueKind.CHOICE,
        listOf("HTTP/1.1", "HTTP/2", "HTTP/3")),
    CACHE_STATUS("缓存状态", "cacheStatus", FilterValueKind.CHOICE,
        listOf("hit", "dynamic", "none", "miss", "revalidated", "expired", "bypass")),
    ACTION("安全性操作", "securityAction", FilterValueKind.CHOICE,
        listOf("unknown", "block", "managed_challenge", "challenge", "js_challenge", "connection_close")),
    SOURCE("安全性来源", "securitySource", FilterValueKind.CHOICE,
        listOf("unknown", "firewallCustom", "firewallManaged", "ratelimit")),
    METHOD("HTTP 方法", "clientRequestHTTPMethodName", FilterValueKind.CHOICE,
        listOf("GET", "POST", "HEAD", "PUT", "DELETE", "PATCH", "OPTIONS")),
    MITIGATED("已缓解", "securityAction", FilterValueKind.CHOICE, listOf("是", "否")),
    BOT_CATEGORY("已验证自动程序类别", "verifiedBotCategory", FilterValueKind.CHOICE,
        listOf("Search Engine Crawler", "Monitoring & Analytics", "Webhooks", "AI Crawler", "Page Preview", "Search Engine Optimization")),
    CREDENTIAL_SCAN("泄露凭证扫描结果", "leakedCredentialCheckResult", FilterValueKind.CHOICE,
        listOf("none", "non_pii", "pii")),
    SERVED_BY("服务提供方", "", FilterValueKind.CHOICE, listOf("由 Cloudflare 提供", "由源服务器提供")),
    HOST("主机", "clientRequestHTTPHost", FilterValueKind.TEXT, allowContains = true),
    PATH("路径", "clientRequestPath", FilterValueKind.TEXT, allowContains = true),
    USER_AGENT("用户代理", "userAgent", FilterValueKind.TEXT, allowContains = true);

    companion object {
        /** 流量页筛选器（官方 15 项） */
        val TRAFFIC = listOf(
            IP, COUNTRY, DEVICE, BROWSER, OS, HTTP_VERSION, CACHE_STATUS,
            ACTION, SOURCE, METHOD, MITIGATED, BOT_CATEGORY, CREDENTIAL_SCAN, SERVED_BY, HOST
        )
        /** 事件页筛选器（事件侧可用字段） */
        val EVENT = listOf(IP, COUNTRY, METHOD, HTTP_VERSION, ACTION, SOURCE, PATH, USER_AGENT, HOST)
    }
}

/** 筛选操作符 */
enum class SecurityFilterOp(val label: String, val suffix: String, val needsContains: Boolean = false) {
    EQ("等于", ""),
    NEQ("不等于", "_neq"),
    CONTAINS("包含", "_like", true),
    NOT_CONTAINS("不包含", "_nlike", true)
}

/** 单条筛选条件 */
data class SecurityFilter(
    val attr: SecurityFilterAttr,
    val op: SecurityFilterOp,
    val values: List<String>
)

/** 维度项（概况/趋势/Top统计共用） */
data class SecuritySegment(val name: String, val count: Long, val percent: Float = 0f)

/** 时间序列点 */
data class SecurityTrendPoint(val label: String, val count: Long)

/** 时间序列 */
data class SecurityTrendSeries(val name: String, val points: List<SecurityTrendPoint> = emptyList())

/** 概况（三段 + 总量） */
data class SecurityOverview(
    val total: Long = 0L,
    val mitigated: SecuritySegment = SecuritySegment("已缓解", 0L),
    val cfServed: SecuritySegment = SecuritySegment("由 Cloudflare 提供", 0L),
    val originServed: SecuritySegment = SecuritySegment("由源服务器提供", 0L)
) {
    val segments: List<SecuritySegment> get() = listOf(mitigated, cfServed, originServed)
}

/** Top 统计卡（来源 IP / 设备类型 / 操作系统 / 缓存状态） */
data class SecurityTopStat(val title: String, val items: List<SecuritySegment> = emptyList())

/** 事件日志列 */
enum class SecurityLogColumn(val label: String, val field: String) {
    ACTION("采取的措施", "action"),
    ASN("ASN", "clientAsn"),
    COUNTRY("国家/地区", "clientCountryName"),
    IP("IP 地址", "clientIP"),
    HOST("主机", "clientRequestHTTPHost"),
    METHOD("方法", "clientRequestHTTPMethodName"),
    HTTP_VERSION("HTTP 版本", "clientRequestHTTPProtocol"),
    PATH("路径", "clientRequestPath"),
    QUERY("查询字符串", "clientRequestQuery"),
    RAY_ID("Ray ID", "rayName"),
    RULE_ID("规则", "description"),
    SERVICE("服务", "source"),
    USER_AGENT("用户代理", "userAgent");

    companion object {
        val DEFAULT = setOf(RULE_ID, IP, HOST, COUNTRY)
        fun byName(name: String) = entries.firstOrNull { it.name == name }
    }
}

/** 事件日志条目（firewallEventsAdaptive） */
data class SecurityLogEntry(
    val datetime: String,
    val rawTime: String,
    val action: String,
    val source: String?,
    val clientIP: String?,
    val clientCountry: String?,
    val clientAsn: String?,
    val host: String?,
    val method: String?,
    val httpVersion: String?,
    val path: String?,
    val query: String?,
    val rayName: String?,
    val ruleId: String?,
    val userAgent: String?
)

/** 国家代码 ↔ 中文名映射（筛选器国家属性用） */
object CountryMapping {
    private val codeToName = mapOf(
        "CN" to "中国", "HK" to "中国香港", "MO" to "中国澳门", "TW" to "中国台湾",
        "US" to "美国", "JP" to "日本", "KR" to "韩国", "SG" to "新加坡",
        "GB" to "英国", "DE" to "德国", "FR" to "法国", "NL" to "荷兰",
        "RU" to "俄罗斯", "BR" to "巴西", "IN" to "印度", "ID" to "印度尼西亚",
        "VN" to "越南", "TH" to "泰国", "MY" to "马来西亚", "PH" to "菲律宾",
        "AU" to "澳大利亚", "CA" to "加拿大", "IT" to "意大利", "ES" to "西班牙",
        "PL" to "波兰", "TR" to "土耳其", "MX" to "墨西哥", "AR" to "阿根廷",
        "CL" to "智利", "CO" to "哥伦比亚", "ZA" to "南非", "EG" to "埃及",
        "NG" to "尼日利亚", "KE" to "肯尼亚", "SA" to "沙特阿拉伯", "AE" to "阿联酋",
        "IL" to "以色列", "IR" to "伊朗", "PK" to "巴基斯坦", "BD" to "孟加拉国",
        "LK" to "斯里兰卡", "NP" to "尼泊尔", "MM" to "缅甸", "KH" to "柬埔寨",
        "MN" to "蒙古", "KZ" to "哈萨克斯坦", "UA" to "乌克兰", "CZ" to "捷克",
        "SK" to "斯洛伐克", "HU" to "匈牙利", "RO" to "罗马尼亚", "BG" to "保加利亚",
        "GR" to "希腊", "PT" to "葡萄牙", "SE" to "瑞典", "NO" to "挪威",
        "DK" to "丹麦", "FI" to "芬兰", "IE" to "爱尔兰", "CH" to "瑞士",
        "AT" to "奥地利", "BE" to "比利时", "LU" to "卢森堡", "IS" to "冰岛",
        "LT" to "立陶宛", "LV" to "拉脱维亚", "EE" to "爱沙尼亚", "SI" to "斯洛文尼亚",
        "HR" to "克罗地亚", "RS" to "塞尔维亚", "BA" to "波黑", "MK" to "北马其顿",
        "AL" to "阿尔巴尼亚", "ME" to "黑山", "MT" to "马耳他", "CY" to "塞浦路斯",
        "NZ" to "新西兰", "FJ" to "斐济", "A1" to "匿名代理", "A2" to "卫星网络",
        "T1" to "Tor 网络", "EU" to "欧洲（区域）", "AP" to "亚太（区域）"
    )
    private val nameToCode = codeToName.entries.associate { (c, n) -> n to c }

    fun codeToName(code: String): String = codeToName[code] ?: code
    fun nameToCode(name: String): String? = nameToCode[name]
    fun search(query: String, limit: Int = 20): List<Pair<String, String>> {
        if (query.isBlank()) return codeToName.entries.take(limit).map { it.key to it.value }
        val q = query.trim().lowercase()
        return codeToName.entries
            .filter { it.key.lowercase().contains(q) || it.value.contains(q) }
            .take(limit)
            .map { it.key to it.value }
    }
}

/**
 * 安全页 GraphQL 查询与解析（口径已用真实 Token 实测校准）。
 *
 * **数据口径（实测）**
 * - 数据集：`httpRequestsAdaptiveGroups`（流量视角）；事件日志：`firewallEventsAdaptive`
 * - 固定过滤 `requestSource: "eyeball"`（终端用户真实请求）→ 与官网总数完全一致（24h=17,774）
 * - 计数直接用 `count`（**不要乘 sampleInterval**；`sum` 里没有 requests 字段）
 * - 三段分类（客户端按 securityAction + cacheStatus + edgeResponseStatus 聚合）：
 *   已缓解 = securityAction ∈ 缓解动作集；由 CF 提供 = 非缓解且（cacheStatus ∈ 命中类 或 3xx 重定向）；其余 = 由源站提供
 * - `_like` / `_nlike` 必须带 `%` 通配（旧实现不带 % 导致筛选恒不命中）
 */
object SecurityAnalyticsParser {

    private const val DATASET = "httpRequestsAdaptiveGroups"
    private const val FW_ADAPTIVE = "firewallEventsAdaptive"
    private const val TOP_N = 5
    private const val GROUP_LIMIT = 100
    private const val SEGMENT_LIMIT = 5000
    private const val TREND_LIMIT = 8000
    private const val LOG_PAGE_SIZE = 50

    /** 缓解动作（官方：block / challenge 类才算缓解；log / skip / allow 不算） */
    private val MITIGATED_ACTIONS = setOf(
        "block", "challenge", "jschallenge", "js_challenge", "managedchallenge",
        "managed_challenge", "connectionclose", "connection_close",
        "managedchallengenoninteractivesolved", "managedchallengeinteractivesolved"
    )
    /** 由 Cloudflare 直接提供的缓存状态 */
    private val CF_SERVED_CACHE = setOf("hit", "revalidated", "expired")
    /** Cloudflare 生成的重定向 */
    private val REDIRECT_CODES = setOf("301", "302", "303", "307", "308")

    // ===== 查询构建 =====

    /** 概况（三段）：按 动作 × 缓存 × 边缘状态 分组，客户端聚合 */
    fun overviewQuery(zoneId: String, range: SecurityTimeRange, filters: List<SecurityFilter>): String =
        buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(DATASET)
            append("(limit: $SEGMENT_LIMIT, orderBy: [count_DESC], filter: {")
            append(filterClause(range, filters))
            append("}) {\n count\n dimensions { securityAction cacheStatus edgeResponseStatus }\n }\n }\n }\n }")
        }

    /** 分组概况（Top N 占比） */
    fun groupOverviewQuery(
        zoneId: String,
        range: SecurityTimeRange,
        groupBy: SecurityGroupBy,
        filters: List<SecurityFilter>
    ): String {
        val dim = groupBy.dimension ?: return overviewQuery(zoneId, range, filters)
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(DATASET)
            append("(limit: $GROUP_LIMIT, orderBy: [count_DESC], filter: {")
            append(filterClause(range, filters))
            append("}) {\n count\n dimensions { $dim }\n }\n }\n }\n }")
        }
    }

    /** 趋势：全部=三段；分组=Top N 各一条 */
    fun trendQuery(
        zoneId: String,
        range: SecurityTimeRange,
        groupBy: SecurityGroupBy,
        filters: List<SecurityFilter>
    ): String {
        val timeDim = range.timeDimension
        val extra = groupBy.dimension?.let { " $it" } ?: " securityAction cacheStatus"
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(DATASET)
            append("(limit: $TREND_LIMIT, filter: {")
            append(filterClause(range, filters))
            append("}) {\n count\n dimensions { $timeDim$extra }\n }\n }\n }\n }")
        }
    }

    /** Top 统计四卡（来源 IP / 设备类型 / 操作系统 / 缓存状态） */
    fun topStatsQuery(zoneId: String, range: SecurityTimeRange, filters: List<SecurityFilter>): String {
        val f = filterClause(range, filters)
        fun block(alias: String, dim: String) =
            "$alias: $DATASET(limit: $TOP_N, orderBy: [count_DESC], filter: {$f}) {\n count\n dimensions { $dim }\n }"
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(block("ipStats", "clientIP")); append('\n')
            append(block("deviceStats", "clientDeviceType")); append('\n')
            append(block("osStats", "userAgentOS")); append('\n')
            append(block("cacheStats", "cacheStatus"))
            append("\n }\n }\n }")
        }
    }

    /** 事件日志：按时间倒序分页（beforeIso 为上一页最后一条时间，做游标） */
    fun logsQuery(
        zoneId: String,
        range: SecurityTimeRange,
        filters: List<SecurityFilter>,
        beforeIso: String? = null
    ): String {
        val f = buildString {
            append(filterClause(range, filters))
            if (!beforeIso.isNullOrBlank()) append(", datetime_lt: \"${escape(beforeIso)}\"")
        }
        return buildString {
            append("query {\n viewer {\n zones(filter: {zoneTag: \"$zoneId\"}) {\n ")
            append(FW_ADAPTIVE)
            append("(limit: $LOG_PAGE_SIZE, orderBy: [datetime_DESC], filter: {$f}) {\n")
            append(" datetime action source clientIP clientCountryName clientAsn clientRequestHTTPHost clientRequestHTTPMethodName clientRequestHTTPProtocol clientRequestPath clientRequestQuery userAgent rayName description\n }\n }\n }\n }")
        }
    }

    /** 时间窗口 + eyeball 固定过滤 + 用户筛选 */
    private fun filterClause(range: SecurityTimeRange, filters: List<SecurityFilter>): String {
        val (s, e) = window(range)
        val base = "datetime_geq: \"$s\", datetime_leq: \"$e\", requestSource: \"eyeball\""
        val user = buildFilter(filters)
        return if (user.isEmpty()) base else "$base, $user"
    }

    /** 用户筛选 → GraphQL filter 片段 */
    private fun buildFilter(filters: List<SecurityFilter>): String = filters.mapNotNull { f ->
        val vals = f.values.filter { it.isNotBlank() }
        if (vals.isEmpty()) return@mapNotNull null
        when (f.attr) {
            // 「已缓解」= 是/否 → 转成 securityAction 过滤
            SecurityFilterAttr.MITIGATED -> {
                val mitigated = vals.first() == "是"
                if (mitigated) "securityAction_in: [${MITIGATED_ACTIONS.joinToString(", ") { "\"$it\"" }}]"
                else "securityAction_in: [\"unknown\", \"allow\", \"log\", \"skip\"]"
            }
            // 「服务提供方」→ 转成 cacheStatus 过滤（由 CF 提供 = 命中类）
            SecurityFilterAttr.SERVED_BY -> {
                val byCf = vals.first().startsWith("由 Cloudflare")
                if (byCf) "cacheStatus_in: [${CF_SERVED_CACHE.joinToString(", ") { "\"$it\"" }}]"
                else "cacheStatus_in: [\"dynamic\", \"miss\", \"none\", \"bypass\"]"
            }
            else -> {
                val field = f.attr.field
                if (vals.size > 1) {
                    "$field${f.op.suffix}: [${vals.joinToString(", ") { "\"${escape(it)}\"" }}]"
                } else if (f.op.needsContains) {
                    // 服务端 LIKE 需要 % 通配
                    "$field${f.op.suffix}: \"%${escape(vals[0])}%\""
                } else {
                    "$field${f.op.suffix}: \"${escape(vals[0])}\""
                }
            }
        }
    }.joinToString(", ")

    private fun escape(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

    // ===== 解析 =====

    /** 解析概况（三段，互斥且合计=总量） */
    fun parseOverview(root: JsonElement): SecurityOverview {
        val groups = datasetArray(root)
        var mitigated = 0L
        var cfServed = 0L
        var originServed = 0L
        groups.forEach { g ->
            val o = g.jsonObject
            val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
            val dims = o["dimensions"]?.jsonObject ?: return@forEach
            val action = dims["securityAction"]?.jsonPrimitive?.content.orEmpty()
            val cache = dims["cacheStatus"]?.jsonPrimitive?.content.orEmpty()
            val status = dims["edgeResponseStatus"]?.jsonPrimitive?.content.orEmpty()
            when {
                action.lowercase() in MITIGATED_ACTIONS -> mitigated += count
                cache.lowercase() in CF_SERVED_CACHE || status in REDIRECT_CODES -> cfServed += count
                else -> originServed += count
            }
        }
        val total = mitigated + cfServed + originServed
        fun seg(name: String, v: Long) = SecuritySegment(name, v, if (total > 0) v * 100f / total else 0f)
        return SecurityOverview(
            total = total,
            mitigated = seg("已缓解", mitigated),
            cfServed = seg("由 Cloudflare 提供", cfServed),
            originServed = seg("由源服务器提供", originServed)
        )
    }

    /** 解析分组概况：Top N + 「其他」 */
    fun parseGroupOverview(root: JsonElement, total: Long): List<SecuritySegment> {
        val groups = datasetArray(root)
        val items = groups.mapNotNull { g ->
            val o = g.jsonObject
            val name = o["dimensions"]?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content
            val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
            if (name.isNullOrBlank()) null else SecuritySegment(name, count)
        }.sortedByDescending { it.count }
        return withOther(items, total)
    }

    /** Top N + 其他（合计与 total 对齐） */
    private fun withOther(items: List<SecuritySegment>, total: Long): List<SecuritySegment> {
        if (items.isEmpty()) return emptyList()
        val base = if (items.size <= TOP_N) items else items.take(TOP_N)
        val covered = base.sumOf { it.count }
        val rest = (total - covered).coerceAtLeast(0L)
        val merged = if (rest > 0 && items.size > TOP_N) base + SecuritySegment("其他", rest) else base
        val sum = merged.sumOf { it.count }
        return merged.map { it.copy(percent = if (sum > 0) it.count * 100f / sum else 0f) }
    }

    /** 解析趋势：全部=三段；分组=Top N + 其他 */
    fun parseTrend(root: JsonElement, range: SecurityTimeRange, groupBy: SecurityGroupBy): List<SecurityTrendSeries> {
        val groups = datasetArray(root)
        if (groups.isEmpty()) return emptyList()
        if (groupBy == SecurityGroupBy.ALL) {
            val mitigated = LinkedHashMap<String, Long>()
            val cfServed = LinkedHashMap<String, Long>()
            val origin = LinkedHashMap<String, Long>()
            groups.forEach { g ->
                val o = g.jsonObject
                val dims = o["dimensions"]?.jsonObject ?: return@forEach
                val label = dims[range.timeDimension]?.jsonPrimitive?.content?.let { formatBucket(it, range) } ?: return@forEach
                val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
                val action = dims["securityAction"]?.jsonPrimitive?.content.orEmpty()
                val cache = dims["cacheStatus"]?.jsonPrimitive?.content.orEmpty()
                when {
                    action.lowercase() in MITIGATED_ACTIONS -> mitigated.merge(label, count) { a, b -> a + b }
                    cache.lowercase() in CF_SERVED_CACHE -> cfServed.merge(label, count) { a, b -> a + b }
                    else -> origin.merge(label, count) { a, b -> a + b }
                }
            }
            val labels = (mitigated.keys + cfServed.keys + origin.keys).sorted()
            fun series(name: String, m: Map<String, Long>) =
                SecurityTrendSeries(name, labels.map { SecurityTrendPoint(it, m[it] ?: 0L) })
            return listOf(
                series("已缓解", mitigated),
                series("由 Cloudflare 提供", cfServed),
                series("由源服务器提供", origin)
            )
        }
        val dim = groupBy.dimension ?: return emptyList()
        val perGroup = LinkedHashMap<String, LinkedHashMap<String, Long>>()
        groups.forEach { g ->
            val o = g.jsonObject
            val dims = o["dimensions"]?.jsonObject ?: return@forEach
            val name = dims[dim]?.jsonPrimitive?.content
            val label = dims[range.timeDimension]?.jsonPrimitive?.content?.let { formatBucket(it, range) } ?: return@forEach
            if (name.isNullOrBlank()) return@forEach
            val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
            perGroup.getOrPut(name) { LinkedHashMap() }.merge(label, count) { a, b -> a + b }
        }
        if (perGroup.isEmpty()) return emptyList()
        val sorted = perGroup.entries.sortedByDescending { e -> e.value.values.sum() }
        val top = sorted.take(TOP_N)
        val rest = sorted.drop(TOP_N)
        val series = top.map { (name, m) ->
            SecurityTrendSeries(name, m.map { SecurityTrendPoint(it.key, it.value) }.sortedBy { it.label })
        }.toMutableList()
        if (rest.isNotEmpty()) {
            val merged = LinkedHashMap<String, Long>()
            rest.forEach { (_, m) -> m.forEach { (k, v) -> merged.merge(k, v) { a, b -> a + b } } }
            series += SecurityTrendSeries("其他", merged.map { SecurityTrendPoint(it.key, it.value) }.sortedBy { it.label })
        }
        return series
    }

    /** 解析 Top 统计四卡 */
    fun parseTopStats(root: JsonElement): List<SecurityTopStat> {
        val zone = zoneObject(root) ?: return emptyList()
        fun read(alias: String, title: String): SecurityTopStat {
            val arr = zone[alias] as? JsonArray ?: return SecurityTopStat(title)
            val items = arr.mapNotNull { g ->
                val o = g.jsonObject
                val name = o["dimensions"]?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content
                val count = o["count"]?.jsonPrimitive?.longOrNull ?: 0L
                if (name.isNullOrBlank()) null else SecuritySegment(name, count)
            }.sortedByDescending { it.count }
            val sum = items.sumOf { it.count }
            return SecurityTopStat(title, items.map { it.copy(percent = if (sum > 0) it.count * 100f / sum else 0f) })
        }
        return listOf(
            read("ipStats", "来源 IP"),
            read("deviceStats", "客户端设备类型"),
            read("osStats", "来源操作系统"),
            read("cacheStats", "缓存状态")
        )
    }

    /** 解析事件日志 */
    fun parseLogs(root: JsonElement): List<SecurityLogEntry> {
        val zone = zoneObject(root) ?: return emptyList()
        val events = zone[FW_ADAPTIVE] as? JsonArray ?: return emptyList()
        return events.mapNotNull { e ->
            val o = e.jsonObject
            val raw = o["datetime"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SecurityLogEntry(
                datetime = formatLogTime(raw),
                rawTime = raw,
                action = o["action"]?.jsonPrimitive?.content.orEmpty(),
                source = o["source"]?.jsonPrimitive?.content,
                clientIP = o["clientIP"]?.jsonPrimitive?.content,
                clientCountry = o["clientCountryName"]?.jsonPrimitive?.content,
                clientAsn = o["clientAsn"]?.jsonPrimitive?.content,
                host = o["clientRequestHTTPHost"]?.jsonPrimitive?.content,
                method = o["clientRequestHTTPMethodName"]?.jsonPrimitive?.content,
                httpVersion = o["clientRequestHTTPProtocol"]?.jsonPrimitive?.content,
                path = o["clientRequestPath"]?.jsonPrimitive?.content,
                query = o["clientRequestQuery"]?.jsonPrimitive?.content,
                rayName = o["rayName"]?.jsonPrimitive?.content,
                ruleId = o["description"]?.jsonPrimitive?.content,
                userAgent = o["userAgent"]?.jsonPrimitive?.content
            )
        }
    }

    // ===== 工具 =====

    private fun zoneObject(root: JsonElement): JsonObject? {
        val zones = root.jsonObject["data"]?.jsonObject?.get("viewer")?.jsonObject?.get("zones") as? JsonArray
        return zones?.firstOrNull()?.jsonObject
    }

    private fun datasetArray(root: JsonElement): JsonArray =
        zoneObject(root)?.get(DATASET) as? JsonArray ?: JsonArray(emptyList())

    private fun window(range: SecurityTimeRange): Pair<String, String> {
        val now = System.currentTimeMillis()
        return isoUtc(now - range.millis) to isoUtc(now)
    }

    private fun isoUtc(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ts))

    /** 时间桶 → 展示标签 */
    private fun formatBucket(raw: String, range: SecurityTimeRange): String = try {
        if (range.isDayBucket) {
            SimpleDateFormat("MM-dd", Locale.US).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)!!
            )
        } else {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(raw) ?: return raw
            val pattern = when (range) {
                SecurityTimeRange.M30 -> "HH:mm"
                SecurityTimeRange.H6, SecurityTimeRange.H12 -> "HH:mm"
                else -> "MM-dd HH时"
            }
            SimpleDateFormat(pattern, Locale.US).format(parsed)
        }
    } catch (e: Exception) {
        raw
    }

    private fun formatLogTime(iso: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso) ?: return iso
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(parsed)
    } catch (e: Exception) {
        iso
    }
}