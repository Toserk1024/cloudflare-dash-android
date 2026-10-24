package io.github.toserk1024.cfdash.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.SecurityFilter
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogColumn
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecurityOverview
import io.github.toserk1024.cfdash.data.model.SecuritySegment
import io.github.toserk1024.cfdash.data.model.SecurityTimeRange
import io.github.toserk1024.cfdash.data.model.SecurityTopStat
import io.github.toserk1024.cfdash.data.model.SecurityTrendSeries
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 安全页 ViewModel（重构版：流量 / 事件 双子页）。
 *
 * - 流量页：概况（三段或分组占比）+ 趋势 + Top 统计，数据源 `httpRequestsAdaptiveGroups`（口径见 SecurityAnalyticsParser）
 * - 事件页：`firewallEventsAdaptive` 日志，每页拉 50 条、每次展示 10 条，滚动到底自动续加载
 * - 两个子页的筛选器互相隔离
 */
class SecurityViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository
) : ViewModel() {

    enum class SecuritySection { TRAFFIC, EVENTS }

    data class SecurityUiState(
        val selectedZone: Zone? = null,
        val section: SecuritySection = SecuritySection.TRAFFIC,
        val groupBy: SecurityGroupBy = SecurityGroupBy.ALL,
        val timeRange: SecurityTimeRange = SecurityTimeRange.H24,
        val eventTimeRange: SecurityTimeRange = SecurityTimeRange.H24,
        val overview: SecurityOverview = SecurityOverview(),
        /** 分组 != 全部时的占比数据（由趋势数据聚合，含「其他」） */
        val groupSegments: List<SecuritySegment> = emptyList(),
        val trend: List<SecurityTrendSeries> = emptyList(),
        val topStats: List<SecurityTopStat> = emptyList(),
        val logs: List<SecurityLogEntry> = emptyList(),
        val logVisibleCount: Int = 0,
        val logHasMore: Boolean = true,
        val trafficFilters: List<SecurityFilter> = emptyList(),
        val eventFilters: List<SecurityFilter> = emptyList(),
        val selectedLogColumns: Set<SecurityLogColumn> = SecurityLogColumn.DEFAULT,
        val trafficLoading: Boolean = false,
        val eventsLoading: Boolean = false,
        val loadingMoreLogs: Boolean = false,
        val refreshing: Boolean = false,
        val trafficError: String? = null,
        val eventsError: String? = null,
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState

    private var trafficJob: Job? = null
    private var eventsJob: Job? = null
    private var moreLogsJob: Job? = null

    init {
        val saved = AppContainer.tokenStore.getSecurityLogColumns()
        val cols = if (saved.isEmpty()) SecurityLogColumn.DEFAULT
        else saved.mapNotNull { SecurityLogColumn.byName(it) }.toSet().ifEmpty { SecurityLogColumn.DEFAULT }
        _uiState.update { it.copy(selectedLogColumns = cols) }
    }

    // ===== 域名 / 子页 / 维度 / 时间 =====

    fun setZone(zone: Zone?) {
        trafficJob?.cancel()
        eventsJob?.cancel()
        moreLogsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedZone = zone,
                groupBy = SecurityGroupBy.ALL,
                timeRange = SecurityTimeRange.H24,
                eventTimeRange = SecurityTimeRange.H24,
                overview = SecurityOverview(),
                groupSegments = emptyList(),
                trend = emptyList(),
                topStats = emptyList(),
                logs = emptyList(),
                logVisibleCount = 0,
                logHasMore = true,
                trafficFilters = emptyList(),
                eventFilters = emptyList(),
                trafficError = null,
                eventsError = null,
                partError = null
            )
        }
        if (zone != null) {
            loadTraffic()
            loadEvents()
        }
    }

    fun setSection(section: SecuritySection) {
        if (_uiState.value.section == section) return
        _uiState.update { it.copy(section = section) }
        // 首次进入事件页时补一次加载
        if (section == SecuritySection.EVENTS && _uiState.value.logs.isEmpty()) loadEvents()
    }

    fun setGroupBy(groupBy: SecurityGroupBy) {
        if (_uiState.value.groupBy == groupBy) return
        _uiState.update { it.copy(groupBy = groupBy) }
        loadTraffic()
    }

    fun setTimeRange(range: SecurityTimeRange) {
        if (_uiState.value.timeRange == range) return
        _uiState.update { it.copy(timeRange = range) }
        loadTraffic()
    }

    fun setEventTimeRange(range: SecurityTimeRange) {
        if (_uiState.value.eventTimeRange == range) return
        _uiState.update { it.copy(eventTimeRange = range) }
        loadEvents()
    }

    // ===== 筛选器（两子页隔离） =====

    fun addTrafficFilter(filter: SecurityFilter) {
        if (filter.values.all { it.isBlank() }) return
        _uiState.update { it.copy(trafficFilters = it.trafficFilters + filter) }
        loadTraffic()
    }

    fun removeTrafficFilter(index: Int) {
        val list = _uiState.value.trafficFilters
        if (index !in list.indices) return
        _uiState.update { it.copy(trafficFilters = list.filterIndexed { i, _ -> i != index }) }
        loadTraffic()
    }

    fun clearTrafficFilters() {
        if (_uiState.value.trafficFilters.isEmpty()) return
        _uiState.update { it.copy(trafficFilters = emptyList()) }
        loadTraffic()
    }

    fun addEventFilter(filter: SecurityFilter) {
        if (filter.values.all { it.isBlank() }) return
        _uiState.update { it.copy(eventFilters = it.eventFilters + filter) }
        loadEvents()
    }

    fun removeEventFilter(index: Int) {
        val list = _uiState.value.eventFilters
        if (index !in list.indices) return
        _uiState.update { it.copy(eventFilters = list.filterIndexed { i, _ -> i != index }) }
        loadEvents()
    }

    fun clearEventFilters() {
        if (_uiState.value.eventFilters.isEmpty()) return
        _uiState.update { it.copy(eventFilters = emptyList()) }
        loadEvents()
    }

    // ===== 日志列（持久化） =====

    fun toggleLogColumn(col: SecurityLogColumn) {
        val cur = _uiState.value.selectedLogColumns
        val next = if (col in cur) cur - col else cur + col
        _uiState.update { it.copy(selectedLogColumns = next) }
        AppContainer.tokenStore.saveSecurityLogColumns(next.map { it.name }.toSet())
    }

    // ===== 刷新 =====

    fun refresh() {
        when (_uiState.value.section) {
            SecuritySection.TRAFFIC -> loadTraffic()
            SecuritySection.EVENTS -> loadEvents()
        }
    }

    // ===== 加载：流量页 =====

    private fun loadTraffic() {
        val zone = _uiState.value.selectedZone ?: return
        trafficJob?.cancel()
        trafficJob = viewModelScope.launch {
            _uiState.update { it.copy(trafficLoading = true, trafficError = null, partError = null) }
            val range = _uiState.value.timeRange
            val gb = _uiState.value.groupBy
            val filters = _uiState.value.trafficFilters
            try {
                val (ov, tr, top) = coroutineScope {
                    val o = async { runCatching { repository.getSecurityOverview(zone.id, range, filters) } }
                    val t = async { runCatching { repository.getSecurityTrend(zone.id, range, gb, filters) } }
                    val s = async { runCatching { repository.getSecurityTopStats(zone.id, range, filters) } }
                    Triple(o.await(), t.await(), s.await())
                }
                val overview = ov.getOrNull()
                val trend = tr.getOrNull().orEmpty()
                val topStats = top.getOrNull().orEmpty()
                val errors = buildList {
                    ov.exceptionOrNull()?.let { add("概况：${it.message ?: "失败"}") }
                    tr.exceptionOrNull()?.let { add("趋势：${it.message ?: "失败"}") }
                    top.exceptionOrNull()?.let { add("统计：${it.message ?: "失败"}") }
                }
                _uiState.update {
                    it.copy(
                        overview = overview ?: it.overview,
                        trend = trend,
                        topStats = topStats,
                        groupSegments = buildGroupSegments(trend, gb),
                        trafficLoading = false,
                        trafficError = if (overview == null) errors.firstOrNull() ?: "加载失败" else null,
                        partError = if (overview != null && errors.isNotEmpty()) errors.joinToString("；") else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(trafficLoading = false, trafficError = e.message ?: "加载安全数据失败") }
            }
        }
    }

    /** 分组占比：用趋势里各序列的总量（趋势已含 Top5 + 其他） */
    private fun buildGroupSegments(
        trend: List<SecurityTrendSeries>,
        groupBy: SecurityGroupBy
    ): List<SecuritySegment> {
        if (groupBy == SecurityGroupBy.ALL) return emptyList()
        val items = trend.map { series ->
            SecuritySegment(series.name, series.points.sumOf { it.count })
        }.filter { it.count > 0 }.sortedByDescending { it.count }
        val total = items.sumOf { it.count }
        return items.map { it.copy(percent = if (total > 0) it.count * 100f / total else 0f) }
    }

    // ===== 加载：事件页（首屏 50 条，展示 10 条） =====

    private fun loadEvents() {
        val zone = _uiState.value.selectedZone ?: return
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(eventsLoading = true, eventsError = null, logs = emptyList(), logVisibleCount = 0, logHasMore = true)
            }
            val range = _uiState.value.eventTimeRange
            val filters = _uiState.value.eventFilters
            runCatching { repository.getSecurityLogs(zone.id, range, filters) }
                .onSuccess { logs ->
                    _uiState.update {
                        it.copy(
                            logs = logs,
                            logVisibleCount = minOf(LOG_DISPLAY_STEP, logs.size),
                            logHasMore = logs.size >= LOG_PAGE_SIZE,
                            eventsLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(eventsLoading = false, eventsError = e.message ?: "加载日志失败") }
                }
        }
    }

    /**
     * 滚动到底部：先展示本地已拉取的下 10 条；本地用尽则按游标再拉一页。
     */
    fun loadMoreLogs() {
        val state = _uiState.value
        val zone = state.selectedZone ?: return
        if (state.loadingMoreLogs) return
        if (state.logVisibleCount < state.logs.size) {
            _uiState.update { it.copy(logVisibleCount = minOf(it.logVisibleCount + LOG_DISPLAY_STEP, it.logs.size)) }
            return
        }
        if (!state.logHasMore) return
        val before = state.logs.lastOrNull()?.rawTime ?: return
        moreLogsJob?.cancel()
        moreLogsJob = viewModelScope.launch {
            _uiState.update { it.copy(loadingMoreLogs = true) }
            runCatching {
                repository.getSecurityLogs(zone.id, state.eventTimeRange, state.eventFilters, before)
            }.onSuccess { more ->
                _uiState.update {
                    val merged = it.logs + more
                    it.copy(
                        logs = merged,
                        logVisibleCount = minOf(it.logVisibleCount + LOG_DISPLAY_STEP, merged.size),
                        logHasMore = more.size >= LOG_PAGE_SIZE,
                        loadingMoreLogs = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(loadingMoreLogs = false) }
            }
        }
    }

    companion object {
        /** 每次展示 10 条 */
        const val LOG_DISPLAY_STEP = 10
        /** 每页拉取 50 条 */
        private const val LOG_PAGE_SIZE = 50
    }
}