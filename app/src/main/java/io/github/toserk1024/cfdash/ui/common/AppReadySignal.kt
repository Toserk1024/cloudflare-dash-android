package io.github.toserk1024.cfdash.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 首屏就绪信号。
 *
 * 用途：切换账号时会显示全屏 Loading 遮罩，原实现用固定 `delay(600)` 猜测"应该加载完了"；
 * 现在 HomeScreen 在首屏关键数据（用户信息 + 域名列表）就绪时调用 [markReady]，
 * MainActivity 收到信号后关闭遮罩（配合最少 400ms 防闪烁、最长 3s 兜底）。
 */
object AppReadySignal {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val isReady: Boolean get() = _ready.value

    fun reset() {
        _ready.value = false
    }

    fun markReady() {
        _ready.value = true
    }
}
