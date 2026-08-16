package io.github.toserk1024.cfdash.data.repository

import io.github.toserk1024.cfdash.data.api.AuthCredential
import io.github.toserk1024.cfdash.data.api.CloudflareApi
import io.github.toserk1024.cfdash.data.api.CloudflareClient
import io.github.toserk1024.cfdash.data.api.CloudflareException
import io.github.toserk1024.cfdash.data.model.ApiResponse
import io.github.toserk1024.cfdash.data.model.AccountRef
import io.github.toserk1024.cfdash.data.model.AnalyticsDistributions
import io.github.toserk1024.cfdash.data.model.AnalyticsParser
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSeries
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.data.model.DnsRecordRequest
import io.github.toserk1024.cfdash.data.model.NelSetting
import io.github.toserk1024.cfdash.data.model.NelSettingRequest
import io.github.toserk1024.cfdash.data.model.NelValue
import io.github.toserk1024.cfdash.data.model.SecurityAnalyticsParser
import io.github.toserk1024.cfdash.data.model.SecurityBreakdownItem
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecurityOverview
import io.github.toserk1024.cfdash.data.model.SecurityTrendPoint
import io.github.toserk1024.cfdash.data.model.TokenVerifyResult
import io.github.toserk1024.cfdash.data.model.User
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZoneAnalyticsItem
import io.github.toserk1024.cfdash.data.model.ZoneSetting
import io.github.toserk1024.cfdash.data.model.ZoneSettingRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 业务仓储层：封装所有 Cloudflare API 调用 */
class CloudflareRepository(private val client: CloudflareClient) {

    /** 验证认证凭据是否有效（使用用户输入，尚未保存） */
    suspend fun verifyCredential(credential: AuthCredential): Boolean = when (credential) {
        is AuthCredential.Token -> {
            val resp: ApiResponse<TokenVerifyResult> = client.get(
                CloudflareApi.VERIFY_TOKEN,
                credentialOverride = credential
            )
            resp.success && resp.result?.status == "active"
        }
        is AuthCredential.GlobalKey -> {
            // Global API Key 无专用验证端点，GET /user 成功即有效
            val resp: ApiResponse<User> = client.get(
                CloudflareApi.USER,
                credentialOverride = credential
            )
            resp.success
        }
    }

    /** 获取当前用户信息 */
    suspend fun getUser(): User =
        client.get<User>(CloudflareApi.USER).result
            ?: throw CloudflareException("获取用户信息失败")

    /** 获取账号列表（GET /accounts，统计账号级数据需要 accountTag） */
    suspend fun getAccounts(): List<AccountRef> =
        client.get<List<AccountRef>>(CloudflareApi.ACCOUNTS).result ?: emptyList()

    /** 分页获取域名列表（name 支持模糊匹配，status 支持 active/pending 等） */
    suspend fun getZones(
        page: Int,
        perPage: Int = 20,
        name: String = "",
        status: String = ""
    ): ApiResponse<List<Zone>> = client.get(
        CloudflareApi.ZONES,
        mapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString(),
            "name" to name,
            "status" to status
        )
    )

    /** 获取域名详情 */
    suspend fun getZone(zoneId: String): Zone =
        client.get<Zone>(CloudflareApi.ZONE.format(zoneId)).result
            ?: throw CloudflareException("获取域名详情失败")

    /** 删除域名（破坏性操作） */
    suspend fun deleteZone(zoneId: String) {
        client.delete<JsonElement>(CloudflareApi.ZONE.format(zoneId))
    }

    /** 获取 Zone 设置（如 development_mode / security_level / ipv6） */
    suspend fun getZoneSetting(zoneId: String, setting: String): ZoneSetting =
        client.get<ZoneSetting>(CloudflareApi.ZONE_SETTING.format(zoneId, setting)).result
            ?: throw CloudflareException("获取设置 $setting 失败")

    /** 更新 Zone 设置（PATCH {"value": ...}） */
    suspend fun updateZoneSetting(zoneId: String, setting: String, value: String): ZoneSetting =
        client.patch<ZoneSetting, ZoneSettingRequest>(
            CloudflareApi.ZONE_SETTING.format(zoneId, setting),
            ZoneSettingRequest(value)
        ).result ?: throw CloudflareException("更新设置 $setting 失败")

    /** 获取 NEL（网络错误记录）设置（value 为对象 {"enabled": bool}，非 on/off 字符串） */
    suspend fun getNel(zoneId: String): Boolean =
        client.get<NelSetting>(CloudflareApi.ZONE_SETTING.format(zoneId, "nel")).result?.value?.enabled ?: false

    /** 更新 NEL（网络错误记录）设置（PATCH {"value": {"enabled": ...}}） */
    suspend fun updateNel(zoneId: String, enabled: Boolean): Boolean =
        client.patch<NelSetting, NelSettingRequest>(
            CloudflareApi.ZONE_SETTING.format(zoneId, "nel"),
            NelSettingRequest(NelValue(enabled))
        ).result?.value?.enabled ?: false

    /** 获取域名级统计（GraphQL Analytics，需 Analytics Read 权限） */
    suspend fun getZoneAnalytics(zoneId: String, range: AnalyticsRange): AnalyticsSum {
        val resp = client.graphql(AnalyticsParser.zoneQuery(zoneId, range))
        return AnalyticsParser.parseZone(resp, range)
    }

    /** 获取账号级统计（遍历账号下所有域名累加，需 Account Analytics Read 权限） */
    suspend fun getAccountAnalytics(accountId: String, range: AnalyticsRange): AnalyticsSum {
        val resp = client.graphql(AnalyticsParser.accountQuery(accountId, range))
        return AnalyticsParser.parseAccount(resp, range)
    }

    /** 获取域名级统计趋势（时间序列，需 Analytics Read 权限） */
    suspend fun getZoneAnalyticsSeries(zoneId: String, range: AnalyticsRange): AnalyticsSeries {
        val resp = client.graphql(AnalyticsParser.zoneSeriesQuery(zoneId, range))
        return AnalyticsParser.parseZoneSeries(resp, range)
    }

    /** 获取账号级统计趋势（时间序列，各域名按时间点合并，需 Account Analytics Read 权限） */
    suspend fun getAccountAnalyticsSeries(accountId: String, range: AnalyticsRange): AnalyticsSeries {
        val resp = client.graphql(AnalyticsParser.accountSeriesQuery(accountId, range))
        return AnalyticsParser.parseAccountSeries(resp, range)
    }

    /** 获取域名级维度分布（国家/状态码，Groups sum 的 countryMap/responseStatusMap，支持 7d/30d） */
    suspend fun getZoneDistributions(zoneId: String, range: AnalyticsRange): AnalyticsDistributions {
        val resp = client.graphql(AnalyticsParser.zoneDistributionsQuery(zoneId, range))
        return AnalyticsParser.parseZoneDistributions(resp, range)
    }

    /** 获取账号级维度分布（各域名同名维度合并） */
    suspend fun getAccountDistributions(accountId: String, range: AnalyticsRange): AnalyticsDistributions {
        val resp = client.graphql(AnalyticsParser.accountDistributionsQuery(accountId, range))
        return AnalyticsParser.parseAccountDistributions(resp, range)
    }

    /** 获取账号级域名拆分（各域名 host 请求量，按请求量降序；AdaptiveGroups 仅支持 24h 范围） */
    suspend fun getAccountZoneBreakdown(accountId: String, range: AnalyticsRange): List<ZoneAnalyticsItem> {
        val resp = client.graphql(AnalyticsParser.accountZoneBreakdownQuery(accountId, range))
        return AnalyticsParser.parseZoneItems(resp, range)
    }

    /** 安全：获取 24h HTTP 概况（总请求/命中）+ 每小时趋势（httpRequests1hGroups，需 Analytics Read 权限） */
    suspend fun getHttpSecurity(zoneId: String): Pair<List<SecurityTrendPoint>, SecurityOverview> {
        val resp = client.graphql(SecurityAnalyticsParser.overviewQuery(zoneId))
        return SecurityAnalyticsParser.parseHttpHourly(resp)
    }

    /** 安全：获取 24h 缓解（firewallEventsAdaptiveGroups，action 缓解动作累计）+ 每小时缓解趋势 */
    suspend fun getMitigation(zoneId: String): Pair<List<SecurityTrendPoint>, Long> {
        val resp = client.graphql(SecurityAnalyticsParser.mitigationQuery(zoneId))
        return SecurityAnalyticsParser.parseMitigation(resp)
    }

    /** 安全：获取分组分布（按所选维度 Top N；国家/设备/IP/HTTP版本/缓存状态 走 httpRequestsAdaptiveGroups，操作/来源 走 firewallEventsAdaptiveGroups） */
    suspend fun getSecurityBreakdown(zoneId: String, groupBy: SecurityGroupBy): List<SecurityBreakdownItem> {
        val resp = client.graphql(SecurityAnalyticsParser.breakdownQuery(zoneId, groupBy))
        return SecurityAnalyticsParser.parseBreakdown(resp, groupBy.dataset)
    }

    /** 安全：获取安全事件日志（firewallEventsAdaptive 最近 200 条，客户端按筛选过滤） */
    suspend fun getSecurityLogs(zoneId: String): List<SecurityLogEntry> {
        val resp = client.graphql(SecurityAnalyticsParser.logsQuery(zoneId))
        return SecurityAnalyticsParser.parseLogs(resp)
    }

    /** 分页获取 DNS 记录（type/name 支持筛选） */
    suspend fun getDnsRecords(
        zoneId: String,
        page: Int,
        perPage: Int = 100,
        type: String = "",
        name: String = ""
    ): ApiResponse<List<DnsRecord>> = client.get(
        CloudflareApi.DNS_RECORDS.format(zoneId),
        mapOf(
            "page" to page.toString(),
            "per_page" to perPage.toString(),
            "type" to type,
            "name" to name
        )
    )

    /** 新建 DNS 记录 */
    suspend fun createDnsRecord(zoneId: String, request: DnsRecordRequest): DnsRecord =
        client.post<DnsRecord, DnsRecordRequest>(CloudflareApi.DNS_RECORDS.format(zoneId), request).result
            ?: throw CloudflareException("创建 DNS 记录失败")

    /** 更新 DNS 记录 */
    suspend fun updateDnsRecord(zoneId: String, recordId: String, request: DnsRecordRequest): DnsRecord =
        client.patch<DnsRecord, DnsRecordRequest>(CloudflareApi.DNS_RECORD.format(zoneId, recordId), request).result
            ?: throw CloudflareException("更新 DNS 记录失败")

    /** 删除 DNS 记录（破坏性操作） */
    suspend fun deleteDnsRecord(zoneId: String, recordId: String) {
        client.delete<JsonElement>(CloudflareApi.DNS_RECORD.format(zoneId, recordId))
    }

    /**
     * 清除 Zone 缓存（POST /zones/{id}/purge_cache）
     * 5 种方式互斥（清除所有时不能与其他方式同时传）：
     * - purgeEverything=true → purge_everything:true（清除该域名全部缓存）
     * - files → 按 URL 精确清除（单文件清除排除项除外）
     * - hosts → 按主机名清除（主机与所提供值之一匹配的所有 URL）
     * - tags → 按 Cache-Tag 清除（响应标头匹配所提供值之一的资源）
     * - prefixes → 按前缀清除（目录下任何资源）
     */
    suspend fun purgeCache(
        zoneId: String,
        purgeEverything: Boolean = false,
        files: List<String> = emptyList(),
        hosts: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        prefixes: List<String> = emptyList()
    ) {
        val body = buildJsonObject {
            if (purgeEverything) {
                put("purge_everything", true)
            } else {
                files.takeIf { it.isNotEmpty() }?.let { put("files", JsonArray(it.map(::JsonPrimitive))) }
                hosts.takeIf { it.isNotEmpty() }?.let { put("hosts", JsonArray(it.map(::JsonPrimitive))) }
                tags.takeIf { it.isNotEmpty() }?.let { put("tags", JsonArray(it.map(::JsonPrimitive))) }
                prefixes.takeIf { it.isNotEmpty() }?.let { put("prefixes", JsonArray(it.map(::JsonPrimitive))) }
            }
        }.toString()
        client.requestRaw("POST", CloudflareApi.PURGE_CACHE.format(zoneId), body, emptyMap())
    }
}