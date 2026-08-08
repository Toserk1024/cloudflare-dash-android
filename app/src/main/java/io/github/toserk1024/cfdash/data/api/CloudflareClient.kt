package io.github.toserk1024.cfdash.data.api

import io.github.toserk1024.cfdash.data.model.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cloudflare API 异常（携带可读错误信息） */
class CloudflareException(message: String, val httpCode: Int = 0) : Exception(message)

/** 全局 Json 配置（忽略未知字段、默认值兜底） */
@PublishedApi
internal val CloudflareJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * 泛型反序列化辅助（inline reified）
 */
@PublishedApi
internal inline fun <reified T> decodeApiResponse(raw: String): ApiResponse<T> =
    CloudflareJson.decodeFromString(raw)

/**
 * Cloudflare API 客户端（OkHttp 封装）
 * @param credentialProvider 每次请求时获取当前认证凭据（Token / GlobalKey）
 */
class CloudflareClient(private val credentialProvider: () -> AuthCredential?) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    internal suspend inline fun <reified RespT> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = decodeApiResponse(requestRaw("GET", path, null, query, credentialOverride))

    /** POST：body 使用具体类型序列化（reified），避免 Any 序列化失败 */
    internal suspend inline fun <reified RespT, reified BodyT> post(
        path: String,
        body: BodyT,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = decodeApiResponse(
        requestRaw("POST", path, CloudflareJson.encodeToString(body), query, credentialOverride)
    )

    /** PATCH：body 使用具体类型序列化（reified） */
    internal suspend inline fun <reified RespT, reified BodyT> patch(
        path: String,
        body: BodyT,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = decodeApiResponse(
        requestRaw("PATCH", path, CloudflareJson.encodeToString(body), query, credentialOverride)
    )

    internal suspend inline fun <reified RespT> delete(
        path: String,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = decodeApiResponse(requestRaw("DELETE", path, null, query, credentialOverride))

    /**
     * GraphQL 查询（POST /graphql）
     * GraphQL 响应不是 REST 的 ApiResponse 包装（data/errors 结构），返回原始解析结果；
     * errors 数组非空时抛出 CloudflareException。
     */
    internal suspend fun graphql(
        query: String,
        credentialOverride: AuthCredential? = null
    ): JsonElement {
        val body = buildJsonObject { put("query", query) }.toString()
        val raw = requestRaw("POST", CloudflareApi.GRAPHQL, body, emptyMap(), credentialOverride)
        val parsed = CloudflareJson.parseToJsonElement(raw)
        (parsed.jsonObject["errors"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { errs ->
            val msg = errs.joinToString("；") {
                it.jsonObject["message"]?.jsonPrimitive?.content ?: "GraphQL 查询失败"
            }
            throw CloudflareException(msg)
        }
        return parsed
    }

    /** 执行请求并返回原始响应体字符串 */
    internal suspend fun requestRaw(
        method: String,
        path: String,
        jsonBody: String?,
        query: Map<String, String>,
        credentialOverride: AuthCredential? = null
    ): String = withContext(Dispatchers.IO) {
        val credential = credentialOverride ?: credentialProvider()
            ?: throw CloudflareException("未配置认证信息，请先登录")

        val urlBuilder = (CloudflareApi.BASE_URL + path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v.isNotBlank()) urlBuilder.addQueryParameter(k, v) }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")

        // 按认证方式添加请求头（OkHttp 禁止 header 含中文等非 ASCII 字符，先校验）
        when (credential) {
            is AuthCredential.Token -> {
                requireAscii(credential.value, "API Token")
                requestBuilder.header("Authorization", "Bearer ${credential.value}")
            }
            is AuthCredential.GlobalKey -> {
                requireAscii(credential.email, "邮箱")
                requireAscii(credential.apiKey, "Global API Key")
                requestBuilder.header("X-Auth-Email", credential.email)
                requestBuilder.header("X-Auth-Key", credential.apiKey)
            }
        }

        val req = when (method) {
            "GET" -> requestBuilder.get().build()
            "DELETE" -> requestBuilder.delete().build()
            "POST" -> requestBuilder.post(jsonBody.orEmpty().toRequestBody(jsonMediaType)).build()
            "PATCH" -> requestBuilder.patch(jsonBody.orEmpty().toRequestBody(jsonMediaType)).build()
            else -> throw CloudflareException("不支持的请求方法: $method")
        }

        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                if (resp.code !in 200..299) {
                    throw parseError(raw, resp.code)
                }
                raw
            }
        } catch (e: CloudflareException) {
            throw e
        } catch (e: IOException) {
            throw CloudflareException("网络请求失败: ${e.message ?: "请检查网络连接"}")
        }
    }

    private fun parseError(raw: String, code: Int): CloudflareException {
        return try {
            val err = decodeApiResponse<JsonElement>(raw)
            val msg = err.errors.joinToString("；") { it.message }
            CloudflareException(
                msg.ifEmpty { "请求失败 (HTTP $code)" },
                code
            )
        } catch (e: Exception) {
            CloudflareException("请求失败 (HTTP $code)", code)
        }
    }

    /** 校验认证字段只能包含 ASCII 可打印字符（OkHttp 禁止 header 含非 ASCII 字符） */
    private fun requireAscii(value: String, name: String) {
        if (!value.all { it.code in 0x21..0x7E }) {
            throw CloudflareException("$name 格式无效：只能包含英文字母、数字与符号，请检查是否粘贴时混入了其他字符")
        }
    }
}