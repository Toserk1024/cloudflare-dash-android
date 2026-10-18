package io.github.toserk1024.cfdash.ui.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import kotlinx.coroutines.CancellationException
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

/**
 * 缓存清除 ViewModel。
 * 当前域名来自全局单一事实源 [ZoneSelectionStore]（本 VM 不再持有域名选择的入口，只保留当前值用于展示）。
 */
class CacheViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository
) : ViewModel() {

    data class CacheUiState(
        val selectedZone: Zone? = null,
        val mode: PurgeMode = PurgeMode.EVERYTHING,
        val input: String = "",
        val busy: Boolean = false,
        val showConfirm: Boolean = false,
        val error: String? = null,
        val resultMessage: String? = null,
        val resultSuccess: Boolean? = null
    )

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState

    init {
        viewModelScope.launch {
            ZoneSelectionStore.selected.collect { zone -> applyZone(zone) }
        }
    }

    private fun applyZone(zone: Zone?) {
        val same = _uiState.value.selectedZone?.id == zone?.id
        if (same) {
            // 同一域名的对象更新：只刷新展示数据，不清空用户正在输入的内容
            if (zone != null) _uiState.update { it.copy(selectedZone = zone) }
            return
        }
        _uiState.update {
            it.copy(
                selectedZone = zone,
                mode = PurgeMode.EVERYTHING,
                input = "",
                error = null,
                resultMessage = null,
                resultSuccess = null
            )
        }
    }

    fun setMode(mode: PurgeMode) {
        _uiState.update { it.copy(mode = mode, error = null, resultMessage = null, resultSuccess = null) }
    }

    fun setInput(input: String) {
        _uiState.update { it.copy(input = input, error = null) }
    }

    private fun parseInput(): List<String> =
        _uiState.value.input.lines().map { it.trim() }.filter { it.isNotBlank() }

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

    fun purge() {
        val zone = _uiState.value.selectedZone ?: return
        val mode = _uiState.value.mode
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, showConfirm = false, error = null, resultMessage = null, resultSuccess = null) }
            try {
                val values = if (mode == PurgeMode.EVERYTHING) emptyList() else parseInput()
                repository.purgeCache(
                    zoneId = zone.id,
                    purgeEverything = mode == PurgeMode.EVERYTHING,
                    files = if (mode == PurgeMode.URL) values else emptyList(),
                    hosts = if (mode == PurgeMode.HOST) values else emptyList(),
                    tags = if (mode == PurgeMode.TAG) values else emptyList(),
                    prefixes = if (mode == PurgeMode.PREFIX) values else emptyList()
                )
                _uiState.update { it.copy(busy = false, resultSuccess = true, resultMessage = "缓存清除成功") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, resultSuccess = false, resultMessage = e.message ?: "缓存清除失败") }
            }
        }
    }
}