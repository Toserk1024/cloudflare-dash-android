package io.github.toserk1024.cfdash.data.state

import io.github.toserk1024.cfdash.data.model.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局选中域名的**单一事实源**。
 *
 * 背景：此前 6 个 ViewModel 各存一份 selectedZone 副本，靠 HomeScreen 手动广播同步，
 * 切换域名时会并发发起 20+ 请求，且各页状态易不一致。
 * 现在：ZoneViewModel 负责加载，选中结果写入本 Store；其余 ViewModel 订阅后自行处理。
 */
object ZoneSelectionStore {

    private val _selected = MutableStateFlow<Zone?>(null)
    val selected: StateFlow<Zone?> = _selected.asStateFlow()

    /** 更新选中域名（允许同 id 但对象内容更新，例如详情刷新） */
    fun select(zone: Zone?) {
        _selected.value = zone
    }

    /** 清空选中（切换账号 / 退出登录时调用） */
    fun clear() {
        _selected.value = null
    }
}
