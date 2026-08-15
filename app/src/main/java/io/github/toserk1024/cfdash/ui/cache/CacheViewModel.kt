package io.github.toserk1024.cfdash.ui.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 缓存清除方式（对应 Cloudflare purge_cache 的 5 种清除） */
enum class PurgeMode(val label: String, val hint: String, val placeholder: String) {
    EVERYTHING("清除所有", "清除该域名下的全部缓存", ""),
    URL("按 URL 清除", "URL 与所提供值精确匹配的资源（单文件清除排除项除外）", "每行一个 URL，如 https://example.com/style.css"),
    HOST("按主机名清除", "主机与所提供值之一匹配的所有 URL 上的资源", "每行一个主机名，如 example.com"),
    TAG("按标签清除", "使用 Cache-Tag 响应标头且标签匹配所提供值之一的资源", "每行一个 Cache-Tag"),
    PREFIX("按前缀清除", "目录路径前缀匹配的任意资源", "每行一个前缀，如 /images/")
}

/** 缓存清除 ViewModel */
class CacheViewModel : ViewModel() {

    data class CacheUiState(
        val zones: List<Zone> = emptyList(),
        val selectedZone: Zone? = null,
        val loadingZones: Boolean = false,
        val mode: PurgeMode = PurgeMode.EVERYTHING,
        val input: String = "",
        val busy: Boolean = false,
        val showConfirm: Boolean = false,
        val showZonePicker: Boolean = false,
        val error: String? = null,
        val resultMessage: String? = null,
        val resultSuccess: Boolean? = null
    )

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState

    init {
        loadZones()
    }

    fun loadZones() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingZones = true, error = null) }
            try {
                val resp = AppContainer.repository.getZones(page = 1, perPage = 50)
                val zones = resp.result ?: emptyList()
                val keep = _uiState.value.selectedZone
                _uiState.update {
                    it.copy(
                        zones = zones,
                        selectedZone = keep ?: zones.firstOrNull(),
                        loadingZones = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loadingZones = false, error = e.message) }
            }
        }
    }

    fun selectZone(zone: Zone) {
        _uiState.update { it.copy(selectedZone = zone, showZonePicker = false) }
    }

    fun showZonePicker() {
        _uiState.update { it.copy(showZonePicker = true) }
    }

    fun dismissZonePicker() {
        _uiState.update { it.copy(showZonePicker = false) }
    }

    fun setMode(mode: PurgeMode) {
        _uiState.update {
            it.copy(mode = mode, error = null, resultMessage = null, resultSuccess = null)
        }
    }

    fun setInput(input: String) {
        _uiState.update { it.copy(input = input, error = null) }
    }

    /** 按行解析输入内容 */
    private fun parseInput(): List<String> =
        _uiState.value.input.lines().map { it.trim() }.filter { it.isNotBlank() }

    /** 发起清除（先弹确认对话框） */
    fun requestPurge() {
        if (_uiState.value.selectedZone == null) {
            _uiState.update { it.copy(error = "请先选择域名") }
            return
        }
        if (_uiState.value.mode != PurgeMode.EVERYTHING && parseInput().isEmpty()) {
            _uiState.update { it.copy(error = "请输入要清除的内容") }
            return
        }
        _uiState.update { it.copy(showConfirm = true, error = null) }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(showConfirm = false) }
    }

    /** 执行清除（确认后调用） */
    fun purge() {
        val zone = _uiState.value.selectedZone ?: return
        val mode = _uiState.value.mode
        viewModelScope.launch {
            _uiState.update {
                it.copy(busy = true, showConfirm = false, error = null, resultMessage = null, resultSuccess = null)
            }
            try {
                val values = if (mode == PurgeMode.EVERYTHING) emptyList() else parseInput()
                AppContainer.repository.purgeCache(
                    zoneId = zone.id,
                    purgeEverything = mode == PurgeMode.EVERYTHING,
                    files = if (mode == PurgeMode.URL) values else emptyList(),
                    hosts = if (mode == PurgeMode.HOST) values else emptyList(),
                    tags = if (mode == PurgeMode.TAG) values else emptyList(),
                    prefixes = if (mode == PurgeMode.PREFIX) values else emptyList()
                )
                _uiState.update { it.copy(busy = false, resultSuccess = true, resultMessage = "缓存清除成功") }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, resultSuccess = false, resultMessage = e.message ?: "缓存清除失败") }
            }
        }
    }
}
