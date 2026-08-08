package io.github.toserk1024.cfdash.data.repository

import io.github.toserk1024.cfdash.data.api.AuthCredential
import io.github.toserk1024.cfdash.data.api.CloudflareApi
import io.github.toserk1024.cfdash.data.api.CloudflareClient
import io.github.toserk1024.cfdash.data.api.CloudflareException
import io.github.toserk1024.cfdash.data.model.ApiResponse
import io.github.toserk1024.cfdash.data.model.AccountRef
import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsParser
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSeries
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.BreakdownDimension
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.data.model.DnsRecordRequest
import io.github.toserk1024.cfdash.data.model.TokenVerifyResult
import io.github.toserk1024.cfdash.data.model.User
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZoneAnalyticsItem
import io.github.toserk1024.cfdash.data.model.ZoneSetting
import io.github.toserk1024.cfdash.data.model.ZoneSettingRequest
import kotlinx.serialization.json.JsonElement

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

    /** 获取域名级维度分布（国家/状态码/缓存状态 Top N） */
    suspend fun getZoneBreakdown(
        zoneId: String,
        range: AnalyticsRange,
        dimension: BreakdownDimension
    ): List<AnalyticsBreakdown> {
        val resp = client.graphql(AnalyticsParser.zoneBreakdownQuery(zoneId, range, dimension))
        return AnalyticsParser.parseZoneBreakdown(resp, range, dimension)
    }

    /** 获取账号级维度分布（各域名同名维度合并） */
    suspend fun getAccountBreakdown(
        accountId: String,
        range: AnalyticsRange,
        dimension: BreakdownDimension
    ): List<AnalyticsBreakdown> {
        val resp = client.graphql(AnalyticsParser.accountBreakdownQuery(accountId, range, dimension))
        return AnalyticsParser.parseAccountBreakdown(resp, range, dimension)
    }

    /** 获取账号级域名拆分（各域名请求量，按请求量降序） */
    suspend fun getAccountZoneBreakdown(accountId: String, range: AnalyticsRange): List<ZoneAnalyticsItem> {
        val resp = client.graphql(AnalyticsParser.accountZoneBreakdownQuery(accountId, range))
        return AnalyticsParser.parseZoneItems(resp, range)
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
}