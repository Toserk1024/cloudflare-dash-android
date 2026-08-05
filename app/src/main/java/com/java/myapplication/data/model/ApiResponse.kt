package com.java.myapplication.data.model

import kotlinx.serialization.Serializable

/** Cloudflare API 统一错误/消息条目 */
@Serializable
data class ApiError(
    val code: Int = 0,
    val message: String = ""
)

/** 分页信息（result_info） */
@Serializable
data class ApiResultInfo(
    val page: Int = 1,
    val per_page: Int = 50,
    val count: Int = 0,
    val total_count: Int = 0,
    val total_pages: Int = 1
)

/** Cloudflare API 统一响应包装 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val errors: List<ApiError> = emptyList(),
    val messages: List<ApiError> = emptyList(),
    val result: T? = null,
    val result_info: ApiResultInfo? = null
)

/** Token 验证结果 */
@Serializable
data class TokenVerifyResult(
    val id: String = "",
    val status: String = ""
)
