package io.github.toserk1024.cfdash.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZoneSetting
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import io.github.toserk1024.cfdash.data.state.ZoneSettingsCache
import io.github.toserk1024.cfdash.data.storage.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局共享域名 ViewModel：域名列表 + 当前选中域名 + 域名详情 + 高级设置。
 * 选中结果写入 [ZoneSelectionStore]（单一事实源），其余 Tab 的 ViewModel 订阅该 Store，
 * 不再由 HomeScreen 手动广播（避免 6 份副本 + 切域名时的请求风暴）。
 */
class ZoneViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository,
    private val tokenStore: TokenStore = AppContainer.tokenStore
) : ViewModel() {

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

    private var zonesJob: Job? = null
    private var detailJob: Job? = null

    init {
        loadZones()
    }

    /** 加载域名列表（首次/强制刷新时拉全量） */
    fun loadZones(force: Boolean = false) {
        if (!force && allZones.isNotEmpty()) return
        zonesJob?.cancel()
        zonesJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val all = mutableListOf<Zone>()
                var page = 1
                var totalPages = 1
                do {
                    val resp = repository.getZones(page = page, perPage = PAGE_SIZE)
                    all += resp.result ?: emptyList()
                    totalPages = resp.result_info?.total_pages ?: 1
                    page++
                } while (page <= totalPages)
                allZones = all
                _uiState.update { it.copy(zones = all, loading = false) }
                restorePersistedZone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载域名失败") }
            }
        }
    }

    /** 启动时恢复上次选中的域名（持久化 id），仅当 id 存在且域名列表有匹配时 */
    private fun restorePersistedZone() {
        if (_uiState.value.selectedZone != null) return
        val persistedId = tokenStore.getSelectedZoneId() ?: return
        val zone = allZones.find { it.id == persistedId } ?: return
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
        ZoneSelectionStore.select(zone)
        loadDetailAndSettings(zone.id)
    }

    /** 选择域名：更新选中并加载详情 + 高级设置 */
    fun selectZone(zone: Zone) {
        tokenStore.saveSelectedZoneId(zone.id)
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
        ZoneSelectionStore.select(zone)
        loadDetailAndSettings(zone.id)
    }

    /** 并行加载域名详情 + 三个高级设置（单项失败不阻塞其他） */
    private fun loadDetailAndSettings(zoneId: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, settingsError = null) }
            coroutineScope {
                async {
                    try {
                        val z = repository.getZone(zoneId)
                        _uiState.update { it.copy(selectedZone = z, detailLoading = false, detailError = null) }
                        ZoneSelectionStore.select(z)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { it.copy(detailLoading = false, detailError = e.message) }
                    }
                }
                async { loadSettings(zoneId) }
            }
        }
    }

    private suspend fun loadSettings(zoneId: String) {
        coroutineScope {
            async {
                try {
                    val s = repository.getZoneSetting(zoneId, "development_mode")
                    _uiState.update { it.copy(devMode = s.value == "on", devModeRemaining = s.time_remaining) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(settingsError = e.message) }
                }
            }
            async {
                try {
                    val s = repository.getZoneSetting(zoneId, "security_level")
                    _uiState.update { it.copy(underAttack = s.value == "under_attack") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(settingsError = e.message) }
                }
            }
            async {
                try {
                    val s = repository.getZoneSetting(zoneId, "ipv6")
                    _uiState.update { it.copy(ipv6 = s.value == "on") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(settingsError = e.message) }
                }
            }
        }
    }

    /** 仅重试高级设置加载 */
    fun refreshSettings() {
        _uiState.update { it.copy(settingsError = null) }
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch { loadSettings(zone.id) }
    }

    /** 重新加载当前域名的详情 + 高级设置（用于详情加载失败后的「重试」按钮） */
    fun reloadDetail() {
        val zone = _uiState.value.selectedZone ?: return
        ZoneSettingsCache.invalidate(zone.id)
        loadDetailAndSettings(zone.id)
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
                val s = repository.updateZoneSetting(zone.id, setting, value)
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
        const val PAGE_SIZE = 50
    }
}