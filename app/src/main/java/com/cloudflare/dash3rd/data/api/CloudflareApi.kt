package com.cloudflare.dash3rd.data.api

/** Cloudflare API 端点到常量 */
object CloudflareApi {
    const val BASE_URL = "https://api.cloudflare.com/client/v4"

    // 认证与用户
    const val VERIFY_TOKEN = "/user/tokens/verify"
    const val USER = "/user"

    // 域名（Zone）
    const val ZONES = "/zones"
    const val ZONE = "/zones/%s"

    // 账号
    const val ACCOUNTS = "/accounts"

    // DNS 记录
    const val DNS_RECORDS = "/zones/%s/dns_records"
    const val DNS_RECORD = "/zones/%s/dns_records/%s"
}