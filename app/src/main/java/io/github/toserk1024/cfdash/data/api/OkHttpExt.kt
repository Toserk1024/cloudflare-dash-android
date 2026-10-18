package io.github.toserk1024.cfdash.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 可取消的异步请求。
 * 协程被取消时会真正 `Call.cancel()`，避免"页面已离开但请求仍在跑、仍在耗流量"。
 */
internal suspend fun OkHttpClient.awaitCall(request: Request): Response =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response)
                } else {
                    // 协程已取消：及时释放连接
                    runCatching { response.close() }
                }
            }
        })
    }

/**
 * 重试包装（仅对可重试错误生效：网络层错误 / 5xx）。
 * 取消异常永不重试。
 *
 * @param attempts 总尝试次数（含首次），默认 3
 * @param initialDelayMs 首次退避时长，之后按 3 倍递增
 */
internal suspend fun <T> withRetry(
    attempts: Int = 3,
    initialDelayMs: Long = 200,
    isRetryable: (Throwable) -> Boolean = { it is IOException },
    block: suspend () -> T
): T {
    var lastError: Throwable? = null
    var delayMs = initialDelayMs
    for (index in 0 until attempts) {
        try {
            return block()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            lastError = e
            if (index == attempts - 1 || !isRetryable(e)) throw e
            delay(delayMs)
            delayMs *= 3
        }
    }
    throw lastError ?: IllegalStateException("请求失败")
}
