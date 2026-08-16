package io.github.toserk1024.cfdash.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.SecurityFilter
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogColumn
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecuritySegment
import io.github.toserk1024.cfdash.data.model.SecurityTimeRange
import io.github.toserk1024.cfdash.data.model.SecurityTrendSeries
import io.github.toserk1024.cfdash.data.model.Zone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 安全分析 ViewModel。
 * 总览 / 日志两个子页：筛选器各自隔离；总览（概况+趋势）用总览筛选器，日志用日志筛选器；
 * 日志列自选并持久化；时间范围各自独立；分组视图联动概况与趋势。
 */
class SecurityViewModel : ViewModel() {

    enum class SecuritySection { OVERVIEW, LOG }

    data class SecurityUiState(
        val selectedZone: Zone? = null,
        val section: SecuritySection = SecuritySection.OVERVIEW,
        val groupBy: SecurityGroupBy = SecurityGroupBy.ALL,
        /** 总览时间范围 */
        val timeRange: SecurityTimeRange = SecurityTimeRange.H24,
        /** 日志时间范围 */
        val logTimeRange: SecurityTimeRange = SecurityTimeRange.H24,
        val overview: List<SecuritySegment> = emptyList(),
        val trend: List<SecurityTrendSeries> = emptyList(),
        val allLogs: List<SecurityLogEntry> = emptyList(),
        val overviewFilters: List<SecurityFilter> = emptyList(),
        val logFilters: List<SecurityFilter> = emptyList(),
        val selectedLogColumns: Set<SecurityLogColumn> = SecurityLogColumn.DEFAULT,
        val overviewLoading: Boolean = true,
        val logLoading: Boolean = true,
        val refreshing: Boolean = false,
        val overviewError: String? = null,
        val logError: String? = null,
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState

    init {
        // 读取日志自选列持久化（无则用默认列）
        val saved = AppContainer.tokenStore.getSecurityLogColumns()
        val cols = if (saved.isEmpty()) SecurityLogColumn.DEFAULT
        else saved.mapNotNull { SecurityLogColumn.byName(it) }.toSet().ifEmpty { SecurityLogColumn.DEFAULT }
        _uiState.update { it.copy(selectedLogColumns = cols) }
    }

    fun setZone(zone: Zone?) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                groupBy = SecurityGroupBy.ALL,
                timeRange = SecurityTimeRange.H24,
                logTimeRange = SecurityTimeRange.H24,
                overview = emptyList(),
                trend = emptyList(),
                allLogs = emptyList(),
                overviewFilters = emptyList(),
                logFilters = emptyList(),
                refreshing = false,
                overviewError = null,
                logError = null,
                partError = null
            )
        }
        if (zone != null) {
            loadOverview()
            loadLog()
        }
    }

    fun setSection(section: SecuritySection) {
        if (_uiState.value.section == section) return
        _uiState.update { it.copy(section = section) }
    }

    fun setGroupBy(groupBy: SecurityGroupBy) {
        if (_uiState.value.groupBy == groupBy) return
        _uiState.update { it.copy(groupBy = groupBy) }
        loadOverview()
    }

    fun setTimeRange(range: SecurityTimeRange) {
        if (_uiState.value.timeRange == range) return
        _uiState.update { it.copy(timeRange = range) }
        loadOverview()
    }

    fun setLogTimeRange(range: SecurityTimeRange) {
        if (_uiState.value.logTimeRange == range) return
        _uiState.update { it.copy(logTimeRange = range) }
        loadLog()
    }

    // ===== 总览筛选器（隔离） =====

    fun addOverviewFilter(filter: SecurityFilter) {
        if (filter.values.all { it.isBlank() }) return
        _uiState.update { it.copy(overviewFilters = it.overviewFilters + filter) }
        loadOverview()
    }

    fun removeOverviewFilter(index: Int) {
        val list = _uiState.value.overviewFilters
        if (index !in list.indices) return
        _uiState.update { it.copy(overviewFilters = list.filterIndexed { i, _ -> i != index }) }
        loadOverview()
    }

    fun clearOverviewFilters() {
        if (_uiState.value.overviewFilters.isEmpty()) return
        _uiState.update { it.copy(overviewFilters = emptyList()) }
        loadOverview()
    }

    // ===== 日志筛选器（隔离） =====

    fun addLogFilter(filter: SecurityFilter) {
        if (filter.values.all { it.isBlank() }) return
        _uiState.update { it.copy(logFilters = it.logFilters + filter) }
        loadLog()
    }

    fun removeLogFilter(index: Int) {
        val list = _uiState.value.logFilters
        if (index !in list.indices) return
        _uiState.update { it.copy(logFilters = list.filterIndexed { i, _ -> i != index }) }
        loadLog()
    }

    fun clearLogFilters() {
        if (_uiState.value.logFilters.isEmpty()) return
        _uiState.update { it.copy(logFilters = emptyList()) }
        loadLog()
    }

    // ===== 日志列选择（持久化） =====

    fun toggleLogColumn(col: SecurityLogColumn) {
        val cur = _uiState.value.selectedLogColumns
        val next = if (col in cur) cur - col else cur + col
        _uiState.update { it.copy(selectedLogColumns = next) }
        AppContainer.tokenStore.saveSecurityLogColumns(next.map { it.name }.toSet())
    }

    // ===== 加载 =====

    fun refresh() {
        loadOverview()
        loadLog()
    }

    private fun loadOverview() {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(overviewLoading = true, overviewError = null) }
            try {
                val range = _uiState.value.timeRange
                val gb = _uiState.value.groupBy
                val filters = _uiState.value.overviewFilters
                val result = coroutineScope {
                    val ov = async { runCatching { AppContainer.repository.getSecurityOverview(zone.id, range, gb, filters) } }
                    val tr = async { runCatching { AppContainer.repository.getSecurityTrend(zone.id, range, gb, filters) } }
                    ov to tr
                }
                val ov: List<SecuritySegment>? = result.first.await().getOrElse { e ->
                    _uiState.update { it.copy(overviewError = e.message ?: "加载概况失败") }; null
                }
                val tr: List<SecurityTrendSeries>? = result.second.await().getOrElse { e ->
                    _uiState.update { it.copy(partError = "趋势：${e.message ?: "加载失败"}") }; null
                }
                _uiState.update {
                    it.copy(overview = ov ?: emptyList(), trend = tr ?: emptyList(), overviewLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(overviewLoading = false, overviewError = e.message ?: "加载安全数据失败") }
            }
        }
    }

    private fun loadLog() {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(logLoading = true, logError = null) }
            try {
                val range = _uiState.value.logTimeRange
                val filters = _uiState.value.logFilters
                val logs = runCatching { AppContainer.repository.getSecurityLogs(zone.id, range, filters) }
                    .getOrElse { e -> _uiState.update { it.copy(logError = e.message ?: "加载日志失败") }; emptyList() }
                _uiState.update { it.copy(allLogs = logs, logLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(logLoading = false, logError = e.message ?: "加载日志失败") }
            }
        }
    }
}