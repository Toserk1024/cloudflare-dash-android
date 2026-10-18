package io.github.toserk1024.cfdash.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZoneSetting
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import io.github.toserk1024.cfdash.data.state.ZoneSettingsCache
import io.github.toserk1024.cfdash.data.state.ZoneSettingsLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 网络优化 ViewModel：网络相关 Zone 设置。
 * 当前域名来自全局单一事实源 [ZoneSelectionStore]。
 * 注意：IPv6（ipv6）由 ZoneViewModel 统一管理（域名页高级设置 + 网络 Tab 两处共享同一状态，天然同步），
 * 本 VM 只管理其余 5 项（含 nel）。
 * 特殊项：Pseudo IPv4 值为 off/add_header/overwrite_header（开关语义：开=add_header、关=off）；
 * NEL（网络错误记录）值为对象 {"enabled": bool}，走专用单项接口。
 */
class NetworkViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository
) : ViewModel() {

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

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            ZoneSelectionStore.selected.collect { zone -> applyZone(zone) }
        }
    }

    private fun applyZone(zone: Zone?) {
        if (_uiState.value.selectedZone?.id == zone?.id) {
            if (zone != null) _uiState.update { it.copy(selectedZone = zone) }
            return
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedZone = zone,
                values = emptyMap(),
                settingsBusy = emptySet(),
                settingsError = null,
                loading = zone != null
            )
        }
        if (zone != null) loadSettings(zone.id)
    }

    /** 并行加载全部网络设置（全量接口优先 + 缺失项回退，单项失败不阻塞其他） */
    private fun loadSettings(zoneId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, settingsError = null) }
            try {
                val stringKeys = ALL_SETTINGS.filter { it != NETWORK_ERROR_LOGGING }
                val result = ZoneSettingsLoader.load(repository, zoneId, stringKeys)
                val values = LinkedHashMap<String, Boolean?>()
                result.values.forEach { (key, value) -> values[key] = parseValue(key, value) }
                val errors = result.errors.toMutableList()
                try {
                    values[NETWORK_ERROR_LOGGING] = repository.getNel(zoneId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.add("nel：${e.message ?: "加载失败"}")
                }
                _uiState.update {
                    it.copy(
                        values = values,
                        loading = false,
                        settingsError = errors.takeIf { list -> list.isNotEmpty() }?.joinToString("；")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, settingsError = e.message) }
            }
        }
    }

    /** 仅重试加载全部设置（作废缓存后重新拉取） */
    fun refreshSettings() {
        val zone = _uiState.value.selectedZone ?: return
        ZoneSettingsCache.invalidate(zone.id)
        _uiState.update { it.copy(values = emptyMap(), settingsError = null) }
        loadSettings(zone.id)
    }

    /** 切换单个设置（开关语义，NEL 走对象值专用方法） */
    fun setSetting(setting: String, on: Boolean) {
        if (setting == NETWORK_ERROR_LOGGING) {
            updateNel(on)
        } else {
            updateSetting(setting, encodeValue(setting, on)) { s ->
                _uiState.update { st -> st.copy(values = st.values + (setting to parseValue(setting, s.value))) }
            }
        }
    }

    /** 更新 NEL（网络错误记录）：值对象 {"enabled": bool} */
    private fun updateNel(on: Boolean) {
        val zone = _uiState.value.selectedZone ?: return
        if (NETWORK_ERROR_LOGGING in _uiState.value.settingsBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = it.settingsBusy + NETWORK_ERROR_LOGGING, settingsError = null) }
            try {
                val enabled = repository.updateNel(zone.id, on)
                _uiState.update { st -> st.copy(values = st.values + (NETWORK_ERROR_LOGGING to enabled)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(settingsError = e.message) }
            } finally {
                _uiState.update { it.copy(settingsBusy = it.settingsBusy - NETWORK_ERROR_LOGGING) }
            }
        }
    }

    private fun updateSetting(setting: String, value: String, onSuccess: (ZoneSetting) -> Unit) {
        val zone = _uiState.value.selectedZone ?: return
        if (setting in _uiState.value.settingsBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = it.settingsBusy + setting, settingsError = null) }
            try {
                val s = repository.updateZoneSetting(zone.id, setting, value)
                ZoneSettingsCache.put(zone.id, setting, s.value)
                onSuccess(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(settingsError = e.message) }
            } finally {
                _uiState.update { it.copy(settingsBusy = it.settingsBusy - setting) }
            }
        }
    }

    companion object {
        const val WEBSOCKETS = "websockets"
        const val PSEUDO_IPV4 = "pseudo_ipv4"
        const val IP_GEOLOCATION = "ip_geolocation"
        const val NETWORK_ERROR_LOGGING = "nel"
        const val ONION_ROUTING = "opportunistic_onion"

        /** 本 VM 管理的全部设置名（IPv6 由 ZoneViewModel 管理，不在此列） */
        val ALL_SETTINGS = listOf(
            WEBSOCKETS, PSEUDO_IPV4, IP_GEOLOCATION, NETWORK_ERROR_LOGGING, ONION_ROUTING
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