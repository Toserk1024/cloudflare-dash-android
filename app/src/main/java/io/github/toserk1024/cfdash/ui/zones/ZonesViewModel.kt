package io.github.toserk1024.cfdash.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 域名列表 ViewModel
 * 策略：首次/下拉刷新时从 API 拉取全量域名缓存到内存；
 * 搜索时仅在本地按关键字过滤，不再请求 API。
 */
class ZonesViewModel : ViewModel() {

    data class ZonesUiState(
        /** 过滤后的显示列表 */
        val zones: List<Zone> = emptyList(),
        /** 首次加载 */
        val loading: Boolean = false,
        /** 下拉刷新 */
        val refreshing: Boolean = false,
        val query: String = "",
        val error: String? = null,
        val deletingId: String? = null,
        val showDeleteDialog: Zone? = null
    )

    private val _uiState = MutableStateFlow(ZonesUiState())
    val uiState: StateFlow<ZonesUiState> = _uiState

    /** 全量域名缓存（内存） */
    private var allZones: List<Zone> = emptyList()

    init {
        loadInitial()
    }

    /** 首次加载 */
    fun loadInitial() {
        if (_uiState.value.loading || allZones.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            fetchAllZones()
            _uiState.update { it.copy(loading = false) }
        }
    }

    /** 下拉刷新：重新请求 API 拉全量 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            fetchAllZones()
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    /** 拉取全量域名（翻页直到最后一页） */
    private suspend fun fetchAllZones() {
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
            applyFilter()
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message ?: "加载域名失败") }
        }
    }

    /** 搜索：仅本地过滤 */
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        applyFilter()
    }

    /** 本地关键字过滤 */
    private fun applyFilter() {
        val q = _uiState.value.query.trim()
        val filtered = if (q.isEmpty()) {
            allZones
        } else {
            allZones.filter { it.name.contains(q, ignoreCase = true) }
        }
        _uiState.update { it.copy(zones = filtered) }
    }

    fun requestDelete(zone: Zone) {
        _uiState.update { it.copy(showDeleteDialog = zone) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    fun deleteZone() {
        val zone = _uiState.value.showDeleteDialog ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = zone.id, showDeleteDialog = null) }
            try {
                AppContainer.repository.deleteZone(zone.id)
                _uiState.update { it.copy(deletingId = null) }
                allZones = allZones.filterNot { it.id == zone.id }
                applyFilter()
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingId = null, error = e.message) }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}