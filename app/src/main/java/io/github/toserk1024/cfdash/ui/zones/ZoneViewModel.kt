package io.github.toserk1024.cfdash.ui.zones

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
 * 全局共享域名 ViewModel：域名列表 + 当前选中域名 + 域名详情 + 高级设置。
 * 供域名 Tab / DNS Tab / 统计 Tab / 缓存 Tab 统一使用（"统一域名选择器"）。
 */
class ZoneViewModel : ViewModel() {

    data class ZoneUiState(
        val zones: List<Zone> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        /** 当前选中域名（含最新详情） */
        val selectedZone: Zone? = null,
        val detailLoading: Boolean = false,
        val detailError: String? = null,
        // ===== 高级设置（null = 未加载/加载失败）=====
        val devMode: Boolean? = null,
        val devModeRemaining: Long = 0,
        val underAttack: Boolean? = null,
        val ipv6: Boolean? = null,
        /** 正在切换的 setting 名集合（独立防抖） */
        val settingsBusy: Set<String> = emptySet(),
        val settingsError: String? = null
    )

    private val _uiState = MutableStateFlow(ZoneUiState())
    val uiState: StateFlow<ZoneUiState> = _uiState

    /** 全量域名缓存（内存） */
    private var allZones: List<Zone> = emptyList()

    init {
        loadZones()
    }

    /** 加载域名列表（首次/强制刷新时拉全量） */
    fun loadZones(force: Boolean = false) {
        if (!force && allZones.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val all = mutableListOf<Zone>()
                var page = 1
                var totalPages = 1
                do {
                    val resp = AppContainer.repository.getZones(page = page, perPage = PAGE_SIZE)
                    all += resp.result ?: emptyList()
                    totalPages = resp.result_info?.total_pages ?: 1
                    page++
                } while (page <= totalPages)
                allZones = all
                _uiState.update { it.copy(zones = all, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载域名失败") }
            }
        }
    }

    /** 选择域名：更新选中并加载详情 + 高级设置 */
    fun selectZone(zone: Zone) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                devMode = null,
                underAttack = null,
                ipv6 = null,
                settingsError = null,
                detailError = null
            )
        }
        loadDetailAndSettings(zone.id)
    }

    /** 并行加载域名详情 + 三个高级设置（单项失败不阻塞其他） */
    private fun loadDetailAndSettings(zoneId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, settingsError = null) }
            coroutineScope {
                async {
                    runCatching { AppContainer.repository.getZone(zoneId) }
                        .onSuccess { z -> _uiState.update { it.copy(selectedZone = z, detailLoading = false, detailError = null) } }
                        .onFailure { e -> _uiState.update { it.copy(detailLoading = false, detailError = e.message) } }
                }
                async { loadSettings(zoneId) }
            }
        }
    }

    private suspend fun loadSettings(zoneId: String) {
        coroutineScope {
            async {
                runCatching { AppContainer.repository.getZoneSetting(zoneId, "development_mode") }
                    .onSuccess { s -> _uiState.update { it.copy(devMode = s.value == "on", devModeRemaining = s.time_remaining) } }
                    .onFailure { e -> _uiState.update { it.copy(settingsError = e.message) } }
            }
            async {
                runCatching { AppContainer.repository.getZoneSetting(zoneId, "security_level") }
                    .onSuccess { s -> _uiState.update { it.copy(underAttack = s.value == "under_attack") } }
                    .onFailure { e -> _uiState.update { it.copy(settingsError = e.message) } }
            }
            async {
                runCatching { AppContainer.repository.getZoneSetting(zoneId, "ipv6") }
                    .onSuccess { s -> _uiState.update { it.copy(ipv6 = s.value == "on") } }
                    .onFailure { e -> _uiState.update { it.copy(settingsError = e.message) } }
            }
        }
    }

    /** 仅重试高级设置加载 */
    fun refreshSettings() {
        _uiState.update { it.copy(settingsError = null) }
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch { loadSettings(zone.id) }
    }

    fun setDevelopmentMode(on: Boolean) =
        updateSetting("development_mode", if (on) "on" else "off") { s ->
            _uiState.update { it.copy(devMode = s.value == "on", devModeRemaining = s.time_remaining) }
        }

    fun setUnderAttack(on: Boolean) =
        updateSetting("security_level", if (on) "under_attack" else "medium") { s ->
            _uiState.update { it.copy(underAttack = s.value == "under_attack") }
        }

    fun setIpv6(on: Boolean) =
        updateSetting("ipv6", if (on) "on" else "off") { s ->
            _uiState.update { it.copy(ipv6 = s.value == "on") }
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
        const val PAGE_SIZE = 50
    }
}