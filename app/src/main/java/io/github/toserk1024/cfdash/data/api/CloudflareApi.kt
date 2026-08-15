package io.github.toserk1024.cfdash.data.api

/** Cloudflare API 端点到常量 */
object CloudflareApi {
    const val BASE_URL = "https://api.cloudflare.com/client/v4"

    // 认证与用户
    const val VERIFY_TOKEN = "/user/tokens/verify"
    const val USER = "/user"

    // 域名（Zone）
    const val ZONES = "/zones"
    const val ZONE = "/zones/%s"

    // Zone 设置（/zones/{id}/settings/{name}）
    const val ZONE_SETTING = "/zones/%s/settings/%s"

    // GraphQL Analytics API
    const val GRAPHQL = "/graphql"

    // 账号
    const val ACCOUNTS = "/accounts"

    // DNS 记录
    const val DNS_RECORDS = "/zones/%s/dns_records"
    const val DNS_RECORD = "/zones/%s/dns_records/%s"

    // 缓存清除
    const val PURGE_CACHE = "/zones/%s/purge_cache"
}