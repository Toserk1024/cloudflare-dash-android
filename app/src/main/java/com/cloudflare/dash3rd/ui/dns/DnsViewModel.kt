package com.cloudflare.dash3rd.ui.dns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudflare.dash3rd.AppContainer
import com.cloudflare.dash3rd.data.model.DnsRecord
import com.cloudflare.dash3rd.data.model.Zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * DNS 记录 ViewModel
 * 策略：域名列表与记录列表均在首次/下拉刷新时拉取全量缓存；
 * 类型筛选与关键字搜索在本地完成，不再请求 API。
 */
class DnsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    /** 从路由参数读取的初始域名（独立页面进入时预选） */
    private val initialZoneId: String? = savedStateHandle["zoneId"]

    data class DnsUiState(
        val zones: List<Zone> = emptyList(),
        val selectedZone: Zone? = null,
        /** 过滤后的显示列表 */
        val records: List<DnsRecord> = emptyList(),
        val filterType: String = "",
        val query: String = "",
        val loadingZones: Boolean = false,
        /** 首次加载记录 */
        val loadingRecords: Boolean = false,
        /** 下拉刷新 */
        val refreshing: Boolean = false,
        val error: String? = null,
        val deletingId: String? = null,
        val showDeleteDialog: DnsRecord? = null,
        val showZonePicker: Boolean = false
    )

    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState

    /** 当前选中域名的全量记录缓存（内存） */
    private var allRecords: List<DnsRecord> = emptyList()

    init {
        loadZones()
    }

    /** 加载域名列表（首次/下拉刷新时请求） */
    fun loadZones() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingZones = true, error = null) }
            try {
                val resp = AppContainer.repository.getZones(page = 1, perPage = 50)
                val zones = resp.result ?: emptyList()
                val keepSelection = _uiState.value.selectedZone
                val matched = initialZoneId?.let { id -> zones.find { it.id == id } }
                val first = keepSelection ?: matched ?: zones.firstOrNull()
                _uiState.update {
                    it.copy(
                        zones = zones,
                        selectedZone = first,
                        loadingZones = false
                    )
                }
                if (first != null) loadAllRecords()
            } catch (e: Exception) {
                _uiState.update { it.copy(loadingZones = false, error = e.message) }
            }
        }
    }

    fun selectZone(zone: Zone) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                showZonePicker = false,
                records = emptyList(),
                filterType = "",
                query = ""
            )
        }
        loadAllRecords()
    }

    /** 类型筛选：仅本地过滤 */
    fun setFilterType(type: String) {
        _uiState.update { it.copy(filterType = type) }
        applyFilter()
    }

    /** 关键字搜索：仅本地过滤 */
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        applyFilter()
    }

    /** 下拉刷新：重新请求 API 拉全量记录 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            try {
                val zone = _uiState.value.selectedZone ?: return@launch
                allRecords = fetchAllRecords(zone.id)
                applyFilter()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(refreshing = false) }
            }
        }
    }

    /** 加载当前选中域名的全部记录 */
    private fun loadAllRecords() {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingRecords = true, error = null) }
            try {
                allRecords = fetchAllRecords(zone.id)
                applyFilter()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(loadingRecords = false) }
            }
        }
    }

    /** 拉取指定域名的全部记录（翻页直到最后一页） */
    private suspend fun fetchAllRecords(zoneId: String): List<DnsRecord> {
        val all = mutableListOf<DnsRecord>()
        var page = 1
        var totalPages = 1
        do {
            val resp = AppContainer.repository.getDnsRecords(
                zoneId = zoneId,
                page = page,
                perPage = PAGE_SIZE
            )
            all += resp.result ?: emptyList()
            totalPages = resp.result_info?.total_pages ?: 1
            page++
        } while (page <= totalPages)
        return all
    }

    /** 本地过滤（类型 + 关键字） */
    private fun applyFilter() {
        val s = _uiState.value
        val type = s.filterType
        val q = s.query.trim()
        val filtered = allRecords.filter { record ->
            (type.isEmpty() || record.type == type) &&
                (q.isEmpty() || record.name.contains(q, ignoreCase = true))
        }
        _uiState.update { it.copy(records = filtered) }
    }

    fun showZonePicker() {
        _uiState.update { it.copy(showZonePicker = true) }
    }

    fun dismissZonePicker() {
        _uiState.update { it.copy(showZonePicker = false) }
    }

    fun requestDelete(record: DnsRecord) {
        _uiState.update { it.copy(showDeleteDialog = record) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    fun deleteRecord() {
        val record = _uiState.value.showDeleteDialog ?: return
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = record.id, showDeleteDialog = null) }
            try {
                AppContainer.repository.deleteDnsRecord(zone.id, record.id)
                _uiState.update { it.copy(deletingId = null) }
                allRecords = allRecords.filterNot { it.id == record.id }
                applyFilter()
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingId = null, error = e.message) }
            }
        }
    }

    /**
     * 应用编辑/新建后的本地同步（无需重新请求 API）。
     * 从编辑/新建页返回列表时调用：编辑的记录原位替换，新建的记录追加到末尾。
     */
    fun syncPendingChanges() {
        val zone = _uiState.value.selectedZone ?: return
        val changes = DnsRecordsSync.takeFor(zone.id)
        if (changes.isEmpty()) return

        val existingIds = allRecords.map { it.id }.toSet()
        allRecords = allRecords.map { old ->
            changes.firstOrNull { it.id == old.id } ?: old
        } + changes.filter { it.id !in existingIds }
        applyFilter()
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}