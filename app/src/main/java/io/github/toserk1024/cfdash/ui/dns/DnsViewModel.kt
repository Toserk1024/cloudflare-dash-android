package io.github.toserk1024.cfdash.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.DnsRecord
import io.github.toserk1024.cfdash.data.model.DnsRecordRequest
import io.github.toserk1024.cfdash.data.model.DnsRecordTypes
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * DNS 记录 ViewModel。
 * 当前域名来自全局单一事实源 [ZoneSelectionStore]。
 * 记录在首次/下拉刷新时拉取全量缓存；类型筛选与关键字搜索在本地完成。
 */
class DnsViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository
) : ViewModel() {

    data class DnsUiState(
        val selectedZone: Zone? = null,
        val records: List<DnsRecord> = emptyList(),
        val filterType: String = "",
        val query: String = "",
        val loadingRecords: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val deletingId: String? = null,
        val showDeleteDialog: DnsRecord? = null,
        // ===== 批量操作（候选框）=====
        val selectionMode: Boolean = false,
        val selectedIds: Set<String> = emptySet(),
        val showBulkDeleteDialog: Boolean = false,
        val bulkProxyTarget: Boolean? = null,
        val bulkBusy: Boolean = false
    )

    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState

    /** 当前选中域名的全量记录缓存（内存） */
    private var allRecords: List<DnsRecord> = emptyList()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            ZoneSelectionStore.selected.collect { zone -> applyZone(zone) }
        }
    }

    /** 全局选中域名变化：同域名只更新对象，换域名则清空筛选并重新拉全量 */
    private fun applyZone(zone: Zone?) {
        if (_uiState.value.selectedZone?.id == zone?.id) {
            if (zone != null) _uiState.update { it.copy(selectedZone = zone) }
            return
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                selectedZone = zone,
                records = emptyList(),
                filterType = "",
                query = "",
                loadingRecords = false,
                refreshing = false,
                error = null,
                selectionMode = false,
                selectedIds = emptySet()
            )
        }
        allRecords = emptyList()
        if (zone != null) loadRecords(refreshing = false)
    }

    private fun loadRecords(refreshing: Boolean) {
        val zone = _uiState.value.selectedZone ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (refreshing) {
                _uiState.update { it.copy(refreshing = true, error = null) }
            } else {
                _uiState.update { it.copy(loadingRecords = true, error = null) }
            }
            try {
                allRecords = fetchAllRecords(zone.id)
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                if (isActive) {
                    _uiState.update { it.copy(loadingRecords = false, refreshing = false) }
                }
            }
        }
    }

    fun refresh() = loadRecords(refreshing = true)

    private suspend fun fetchAllRecords(zoneId: String): List<DnsRecord> {
        val all = mutableListOf<DnsRecord>()
        var page = 1
        var totalPages = 1
        do {
            val resp = repository.getDnsRecords(zoneId = zoneId, page = page, perPage = PAGE_SIZE)
            all += resp.result ?: emptyList()
            totalPages = resp.result_info?.total_pages ?: 1
            page++
        } while (page <= totalPages)
        return all
    }

    fun setFilterType(type: String) {
        _uiState.update { it.copy(filterType = type) }
        applyFilter()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        applyFilter()
    }

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
                repository.deleteDnsRecord(zone.id, record.id)
                allRecords = allRecords.filterNot { it.id == record.id }
                _uiState.update { it.copy(deletingId = null) }
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingId = null, error = e.message) }
            }
        }
    }

    /** 消费编辑页保存后的变更队列（本地更新缓存，不重新请求 API） */
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

    // ===== 批量操作（候选框）=====

    fun setSelectionMode(enabled: Boolean) {
        _uiState.update { it.copy(selectionMode = enabled, selectedIds = if (enabled) it.selectedIds else emptySet()) }
    }

    fun toggleSelect(id: String) {
        _uiState.update { s -> s.copy(selectedIds = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id) }
    }

    fun setSelectAll(selected: Boolean) {
        _uiState.update { it.copy(selectedIds = if (selected) it.records.map { r -> r.id }.toSet() else emptySet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun requestBulkDelete() {
        _uiState.update { it.copy(showBulkDeleteDialog = true) }
    }

    fun dismissBulkDelete() {
        _uiState.update { it.copy(showBulkDeleteDialog = false) }
    }

    fun requestBulkProxy(proxied: Boolean) {
        _uiState.update { it.copy(bulkProxyTarget = proxied) }
    }

    fun dismissBulkProxy() {
        _uiState.update { it.copy(bulkProxyTarget = null) }
    }

    fun bulkDelete() {
        val zone = _uiState.value.selectedZone ?: return
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(bulkBusy = true, showBulkDeleteDialog = false) }
            val remaining = allRecords.toMutableList()
            var firstError: String? = null
            ids.forEach { id ->
                try {
                    repository.deleteDnsRecord(zone.id, id)
                    remaining.removeAll { it.id == id }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (firstError == null) firstError = e.message
                }
            }
            allRecords = remaining
            _uiState.update { it.copy(bulkBusy = false, selectedIds = emptySet(), error = firstError) }
            applyFilter()
        }
    }

    fun bulkSetProxy(proxied: Boolean) {
        val zone = _uiState.value.selectedZone ?: return
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(bulkBusy = true, bulkProxyTarget = null) }
            val remaining = allRecords.toMutableList()
            var firstError: String? = null
            ids.forEach { id ->
                val record = remaining.find { it.id == id } ?: return@forEach
                if (record.type !in DnsRecordTypes.PROXIABLE) return@forEach
                try {
                    repository.updateDnsRecord(
                        zone.id, id,
                        DnsRecordRequest(
                            type = record.type,
                            name = record.name,
                            content = record.content,
                            ttl = record.ttl,
                            proxied = proxied,
                            priority = record.priority,
                            comment = record.comment,
                            tags = record.tags,
                            data = record.data
                        )
                    )
                    val idx = remaining.indexOf(record)
                    if (idx >= 0) remaining[idx] = record.copy(proxied = proxied)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (firstError == null) firstError = e.message
                }
            }
            allRecords = remaining
            _uiState.update { it.copy(bulkBusy = false, selectedIds = emptySet(), error = firstError) }
            applyFilter()
        }
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}