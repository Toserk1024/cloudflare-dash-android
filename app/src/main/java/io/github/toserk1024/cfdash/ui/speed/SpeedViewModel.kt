package io.github.toserk1024.cfdash.ui.speed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZoneSetting
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 速度优化 ViewModel：协议优化 / 内容优化 两类 Zone 设置（均为 on/off 开关）。
 * 当前域名由全局域名选择器（HomeScreen 的 ZoneViewModel）传入，本 VM 不维护域名列表。
 * 开关样式沿用域名页高级设置（AdvancedSwitchRow + settingsBusy 独立防抖）。
 */
class SpeedViewModel : ViewModel() {

    data class SpeedUiState(
        val selectedZone: Zone? = null,
        /** 各设置当前值（key = 设置名，value = on/off，null = 未加载/加载失败） */
        val values: Map<String, Boolean?> = emptyMap(),
        /** 正在切换的 setting 名集合（独立防抖） */
        val settingsBusy: Set<String> = emptySet(),
        val settingsError: String? = null,
        val loading: Boolean = false
    ) {
        fun value(setting: String): Boolean? = values[setting]
    }

    private val _uiState = MutableStateFlow(SpeedUiState())
    val uiState: StateFlow<SpeedUiState> = _uiState

    /** 由 HomeScreen 响应全局选中域名变化时调用：切换域名即重载全部设置 */
    fun setZone(zone: Zone?) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                values = emptyMap(),
                settingsBusy = emptySet(),
                settingsError = null,
                loading = false
            )
        }
        if (zone != null) loadSettings(zone.id)
    }

    /** 并行加载全部速度设置（单项失败不阻塞其他） */
    private fun loadSettings(zoneId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, settingsError = null) }
            coroutineScope {
                ALL_SETTINGS.forEach { setting ->
                    async {
                        runCatching { AppContainer.repository.getZoneSetting(zoneId, setting) }
                            .onSuccess { s ->
                                _uiState.update { st -> st.copy(values = st.values + (setting to parseValue(setting, s.value))) }
                            }
                            .onFailure { e -> _uiState.update { st -> st.copy(settingsError = e.message) } }
                    }
                }
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    /** 仅重试加载全部设置（不重置选中域名） */
    fun refreshSettings() {
        _uiState.update { it.copy(settingsError = null) }
        val zone = _uiState.value.selectedZone ?: return
        _uiState.update { it.copy(values = emptyMap()) }
        loadSettings(zone.id)
    }

    /** 切换单个设置（开关语义，含特殊值映射） */
    fun setSetting(setting: String, on: Boolean) {
        updateSetting(setting, encodeValue(setting, on)) { s ->
            _uiState.update { st -> st.copy(values = st.values + (setting to parseValue(setting, s.value))) }
        }
    }

    private fun updateSetting(setting: String, value: String, onSuccess: (ZoneSetting) -> Unit) {
        val zone = _uiState.value.selectedZone ?: return
        if (setting in _uiState.value.settingsBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = it.settingsBusy + setting, settingsError = null) }
            try {
                val s = AppContainer.repository.updateZoneSetting(zone.id, setting, value)
                onSuccess(s)
            } catch (e: Exception) {
                _uiState.update { it.copy(settingsError = e.message) }
            } finally {
                _uiState.update { it.copy(settingsBusy = it.settingsBusy - setting) }
            }
        }
    }

    companion object {
        // ===== 协议优化 =====
        const val HTTP2 = "http2"
        /** HTTP/2 到源服务器：origin_max_http_version（值 "2"=HTTP/2 开 / "1"=HTTP/1.1 关） */
        const val HTTP2_ORIGIN = "origin_max_http_version"
        const val HTTP3 = "http3"
        const val ZERO_RTT = "0rtt"
        // ===== 内容优化 =====
        const val SPEED_BRAIN = "speed_brain"
        const val FONTS = "fonts"
        const val EARLY_HINTS = "early_hints"
        const val ROCKET_LOADER = "rocket_loader"

        /** 全部速度设置名（协议 + 内容），加载时并行遍历 */
        val ALL_SETTINGS = listOf(
            HTTP2, HTTP2_ORIGIN, HTTP3, ZERO_RTT,
            SPEED_BRAIN, FONTS, EARLY_HINTS, ROCKET_LOADER
        )

        /** 读取值 → 开关态（HTTP/2 到源：origin_max_http_version 值 "2"=开、"1"=关） */
        private fun parseValue(setting: String, value: String): Boolean = when (setting) {
            HTTP2_ORIGIN -> value == "2"
            else -> value == "on"
        }

        /** 开关态 → 写入值 */
        private fun encodeValue(setting: String, on: Boolean): String = when (setting) {
            HTTP2_ORIGIN -> if (on) "2" else "1"
            else -> if (on) "on" else "off"
        }
    }
}
