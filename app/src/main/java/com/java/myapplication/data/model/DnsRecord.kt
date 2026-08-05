package com.java.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Cloudflare DNS 记录 */
@Serializable
data class DnsRecord(
    val id: String = "",
    val zone_id: String = "",
    val zone_name: String = "",
    val name: String = "",
    val type: String = "",
    val content: String = "",
    val proxiable: Boolean = false,
    val proxied: Boolean = false,
    val ttl: Long = 1,
    val locked: Boolean = false,
    val priority: Long? = null,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    val created_on: String? = null,
    val modified_on: String? = null,
    val meta: JsonObject? = null,
    val data: JsonObject? = null
)

/** 创建/更新 DNS 记录请求体 */
@Serializable
data class DnsRecordRequest(
    val type: String,
    val name: String,
    val content: String,
    val ttl: Long = 1,
    val proxied: Boolean = false,
    val priority: Long? = null,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    val data: JsonObject? = null
)

/** 常见 DNS 记录类型（用于筛选与新建） */
object DnsRecordTypes {
    val ALL = listOf(
        "A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA", "CERT", "DNSKEY", "DS", "NAPTR", "SMIMEA", "SSHFP", "SVCB", "HTTPS", "TLSA", "URI"
    )

    /** 可被 Cloudflare 代理的记录类型 */
    val PROXIABLE = setOf("A", "AAAA", "CNAME")

    /** 需要 priority 字段的类型 */
    val HAS_PRIORITY = setOf("MX", "URI")

    /** 使用 data 对象的类型 */
    val HAS_DATA = setOf("SRV", "CAA", "SVCB", "HTTPS", "SSHFP", "TLSA", "NAPTR")
}
