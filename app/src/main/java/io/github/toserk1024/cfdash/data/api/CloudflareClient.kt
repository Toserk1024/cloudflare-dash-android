package io.github.toserk1024.cfdash.data.api

import io.github.toserk1024.cfdash.data.model.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cloudflare API 异常（携带可读错误信息与 HTTP 状态码） */
class CloudflareException(message: String, val httpCode: Int = HTTP_CODE_UNKNOWN) : Exception(message) {
    companion object {
        /** 网络层错误（连接失败/超时等），可重试 */
        const val HTTP_CODE_NETWORK = -1
        /** 未知错误（非网络层，不重试） */
        const val HTTP_CODE_UNKNOWN = 0
    }
}

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

    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** GraphQL Analytics 查询可能较慢，单独放宽读超时 */
    private val longTimeoutClient: OkHttpClient = defaultClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    internal suspend inline fun <reified RespT> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = withContext(Dispatchers.Default) {
        // 反序列化放到 Default 线程：启动期并发请求时，解析大响应（安全日志/统计）不应占用主线程
        decodeApiResponse(requestRaw("GET", path, null, query, credentialOverride))
    }

    /** POST：body 使用具体类型序列化（reified），避免 Any 序列化失败 */
    internal suspend inline fun <reified RespT, reified BodyT> post(
        path: String,
        body: BodyT,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = withContext(Dispatchers.Default) {
        decodeApiResponse(
            requestRaw("POST", path, CloudflareJson.encodeToString(body), query, credentialOverride)
        )
    }

    /** PATCH：body 使用具体类型序列化（reified） */
    internal suspend inline fun <reified RespT, reified BodyT> patch(
        path: String,
        body: BodyT,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = withContext(Dispatchers.Default) {
        decodeApiResponse(
            requestRaw("PATCH", path, CloudflareJson.encodeToString(body), query, credentialOverride)
        )
    }

    internal suspend inline fun <reified RespT> delete(
        path: String,
        query: Map<String, String> = emptyMap(),
        credentialOverride: AuthCredential? = null
    ): ApiResponse<RespT> = withContext(Dispatchers.Default) {
        decodeApiResponse(requestRaw("DELETE", path, null, query, credentialOverride))
    }

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
        // GraphQL 响应可能很大（日志/统计），同样放到 Default 线程解析
        val parsed = withContext(Dispatchers.Default) { CloudflareJson.parseToJsonElement(raw) }
        (parsed.jsonObject["errors"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { errs ->
            val msg = errs.joinToString("；") {
                it.jsonObject["message"]?.jsonPrimitive?.content ?: "GraphQL 查询失败"
            }
            throw CloudflareException(msg)
        }
        return parsed
    }

    /**
     * 执行请求并返回原始响应体字符串。
     * - 可取消：协程取消会真正 `Call.cancel()`
     * - 自动重试：仅网络层错误与 5xx（最多 3 次尝试，200ms / 600ms 退避）
     * - 错误中文化：401 / 403 / 429 / 5xx 附带可操作提示
     */
    internal suspend fun requestRaw(
        method: String,
        path: String,
        jsonBody: String?,
        query: Map<String, String>,
        credentialOverride: AuthCredential? = null
    ): String = withRetry(
        attempts = 3,
        initialDelayMs = 200,
        isRetryable = { e ->
            val code = (e as? CloudflareException)?.httpCode
            e is IOException ||
                (code != null && (code == CloudflareException.HTTP_CODE_NETWORK || code >= 500))
        }
    ) {
        executeOnce(method, path, jsonBody, query, credentialOverride)
    }

    private suspend fun executeOnce(
        method: String,
        path: String,
        jsonBody: String?,
        query: Map<String, String>,
        credentialOverride: AuthCredential?
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

        val client = if (method == "POST" && path == CloudflareApi.GRAPHQL) longTimeoutClient else defaultClient

        try {
            client.awaitCall(req).use { resp ->
                val raw = resp.body?.string() ?: ""
                if (resp.code !in 200..299) {
                    throw parseError(raw, resp.code)
                }
                raw
            }
        } catch (e: CloudflareException) {
            throw e
        } catch (e: IOException) {
            throw CloudflareException(
                "网络请求失败：${e.message ?: "请检查网络连接"}",
                CloudflareException.HTTP_CODE_NETWORK
            )
        }
    }

    private fun parseError(raw: String, code: Int): CloudflareException {
        val apiMessage = try {
            decodeApiResponse<JsonElement>(raw).errors.joinToString("；") { it.message }.trim()
        } catch (e: Exception) {
            ""
        }
        val hint = when (code) {
            401 -> "认证失败：凭据无效或已过期，请重新登录"
            403 -> "权限不足：当前凭据缺少该操作所需的 Cloudflare 权限，请在控制台为 Token 补充权限"
            429 -> "请求过于频繁：已被 Cloudflare 限流，请稍后再试"
            in 500..599 -> "Cloudflare 服务暂时不可用（HTTP $code）"
            else -> null
        }
        val message = when {
            hint != null && apiMessage.isNotBlank() -> "$hint（$apiMessage）"
            hint != null -> hint
            apiMessage.isNotBlank() -> apiMessage
            else -> "请求失败 (HTTP $code)"
        }
        return CloudflareException(message, code)
    }

    /** 校验认证字段只能包含 ASCII 可打印字符（OkHttp 禁止 header 含非 ASCII 字符） */
    private fun requireAscii(value: String, name: String) {
        if (!value.all { it.code in 0x21..0x7E }) {
            throw CloudflareException("$name 格式无效：只能包含英文字母、数字与符号，请检查是否粘贴时混入了其他字符")
        }
    }
}