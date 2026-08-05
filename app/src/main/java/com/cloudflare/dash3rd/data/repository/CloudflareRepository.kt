package com.cloudflare.dash3rd.data.repository

import com.cloudflare.dash3rd.data.api.CloudflareApi
import com.cloudflare.dash3rd.data.api.CloudflareClient
import com.cloudflare.dash3rd.data.api.CloudflareException
import com.cloudflare.dash3rd.data.model.ApiResponse
import com.cloudflare.dash3rd.data.model.DnsRecord
import com.cloudflare.dash3rd.data.model.DnsRecordRequest
import com.cloudflare.dash3rd.data.model.TokenVerifyResult
import com.cloudflare.dash3rd.data.model.User
import com.cloudflare.dash3rd.data.model.Zone
import kotlinx.serialization.json.JsonElement

/** 业务仓储层：封装所有 Cloudflare API 调用 */
class CloudflareRepository(private val client: CloudflareClient) {

    /** 验证 Token 是否有效（使用用户输入的 Token，尚未保存） */
    suspend fun verifyToken(token: String): Boolean {
        val resp: ApiResponse<TokenVerifyResult> = client.get(
            CloudflareApi.VERIFY_TOKEN,
            tokenOverride = token
        )
        return resp.success && resp.result?.status == "active"
    }

    /** 获取当前用户信息 */
    suspend fun getUser(): User =
        client.get<User>(CloudflareApi.USER).result
            ?: throw CloudflareException("获取用户信息失败")

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