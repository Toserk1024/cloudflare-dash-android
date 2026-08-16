package io.github.toserk1024.cfdash.ui.network

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
 * 网络优化 ViewModel：网络相关 Zone 设置。
 * 当前域名由全局域名选择器（HomeScreen 的 ZoneViewModel）传入。
 * 注意：IPv6（ipv6）由 ZoneViewModel 统一管理（域名页高级设置 + 网络 Tab 两处共享同一状态，天然同步），
 * 本 VM 只管理其余 6 项。
 * 特殊项：Pseudo IPv4 值为 off/add_header/overwrite_header（开关语义：开=add_header、关=off）。
 */
class NetworkViewModel : ViewModel() {

    data class NetworkUiState(
        val selectedZone: Zone? = null,
        /** 各设置当前开关态（key = 设置名，value = on/off，null = 未加载/加载失败） */
        val values: Map<String, Boolean?> = emptyMap(),
        /** 正在切换的 setting 名集合（独立防抖） */
        val settingsBusy: Set<String> = emptySet(),
        val settingsError: String? = null,
        val loading: Boolean = false
    ) {
        fun value(setting: String): Boolean? = values[setting]
    }

    private val _uiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = _uiState

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

    /** 并行加载全部网络设置（单项失败不阻塞其他） */
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

    /** 切换单个设置（开关语义） */
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
        const val GRPC = "grpc"
        const val WEBSOCKETS = "websockets"
        const val PSEUDO_IPV4 = "pseudo_ipv4"
        const val IP_GEOLOCATION = "ip_geolocation"
        const val NETWORK_ERROR_LOGGING = "web_network_error_logging"
        const val ONION_ROUTING = "opportunistic_onion"

        /** 本 VM 管理的全部设置名（IPv6 由 ZoneViewModel 管理，不在此列） */
        val ALL_SETTINGS = listOf(
            GRPC, WEBSOCKETS, PSEUDO_IPV4, IP_GEOLOCATION, NETWORK_ERROR_LOGGING, ONION_ROUTING
        )

        /** 读取值 → 开关态 */
        private fun parseValue(setting: String, value: String): Boolean = when (setting) {
            PSEUDO_IPV4 -> value == "add_header" || value == "overwrite_header"
            else -> value == "on"
        }

        /** 开关态 → 写入值 */
        private fun encodeValue(setting: String, on: Boolean): String = when (setting) {
            PSEUDO_IPV4 -> if (on) "add_header" else "off"
            else -> if (on) "on" else "off"
        }
    }
}