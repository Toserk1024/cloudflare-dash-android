package io.github.toserk1024.cfdash.ui.zones

import androidx.lifecycle.SavedStateHandle
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

class ZoneDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val zoneId: String = checkNotNull(savedStateHandle["zoneId"])

    data class ZoneDetailUiState(
        val zone: Zone? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val deleting: Boolean = false,
        val deleted: Boolean = false,
        // ===== 高级设置（null = 未加载/加载失败）=====
        val devMode: Boolean? = null,
        val devModeRemaining: Long = 0,
        val underAttack: Boolean? = null,
        val ipv6: Boolean? = null,
        // 正在切换的 setting 名（防连点，非 null 时禁用全部开关）
        val settingsBusy: String? = null,
        val settingsError: String? = null
    )

    private val _uiState = MutableStateFlow(ZoneDetailUiState())
    val uiState: StateFlow<ZoneDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, settingsError = null) }
            try {
                val zone = AppContainer.repository.getZone(zoneId)
                _uiState.update { it.copy(zone = zone, loading = false, error = null) }
                loadSettings()
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** 并行加载三个高级设置（单项失败不阻塞其他） */
    private fun loadSettings() {
        viewModelScope.launch {
            coroutineScope {
                async {
                    runCatching { AppContainer.repository.getZoneSetting(zoneId, "development_mode") }
                        .onSuccess { s ->
                            _uiState.update { it.copy(devMode = s.value == "on", devModeRemaining = s.time_remaining) }
                        }
                }
                async {
                    runCatching { AppContainer.repository.getZoneSetting(zoneId, "security_level") }
                        .onSuccess { s ->
                            _uiState.update { it.copy(underAttack = s.value == "under_attack") }
                        }
                }
                async {
                    runCatching { AppContainer.repository.getZoneSetting(zoneId, "ipv6") }
                        .onSuccess { s ->
                            _uiState.update { it.copy(ipv6 = s.value == "on") }
                        }
                }
            }
        }
    }

    // ===== 高级设置切换 =====

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

    /** 通用设置更新：切换中防连点，成功后回调更新状态，失败保留原值并提示 */
    private fun updateSetting(setting: String, value: String, onSuccess: (ZoneSetting) -> Unit) {
        if (_uiState.value.settingsBusy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = setting, settingsError = null) }
            try {
                val s = AppContainer.repository.updateZoneSetting(zoneId, setting, value)
                onSuccess(s)
            } catch (e: Exception) {
                _uiState.update { it.copy(settingsError = e.message) }
            } finally {
                _uiState.update { it.copy(settingsBusy = null) }
            }
        }
    }

    fun deleteZone() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true) }
            try {
                AppContainer.repository.deleteZone(zoneId)
                _uiState.update { it.copy(deleting = false, deleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false, error = e.message) }
            }
        }
    }
}