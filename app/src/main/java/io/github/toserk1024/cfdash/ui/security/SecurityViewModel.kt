package io.github.toserk1024.cfdash.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.SecurityFilter
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
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
 * 时间范围（半小时/3h/12h/1天）作用于概况/趋势/日志/分组；
 * 分组视图联动概况与趋势；全局筛选器（AND 叠加）经 GraphQL filter 过滤概况/趋势/日志。
 */
class SecurityViewModel : ViewModel() {

    data class SecurityUiState(
        val selectedZone: Zone? = null,
        val groupBy: SecurityGroupBy = SecurityGroupBy.ALL,
        val timeRange: SecurityTimeRange = SecurityTimeRange.H24,
        /** 概况段（分组=全部→回源/命中/缓解；分组=X→TopN 分组占比） */
        val overview: List<SecuritySegment> = emptyList(),
        /** 趋势序列（分组=全部单序列；分组=X Top5） */
        val trend: List<SecurityTrendSeries> = emptyList(),
        /** 日志（服务端已按筛选器过滤） */
        val allLogs: List<SecurityLogEntry> = emptyList(),
        /** 已应用筛选器（AND 叠加） */
        val filters: List<SecurityFilter> = emptyList(),
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState

    /** 由 HomeScreen 响应全局选中域名变化时调用：重置并重载全部安全数据 */
    fun setZone(zone: Zone?) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                groupBy = SecurityGroupBy.ALL,
                timeRange = SecurityTimeRange.H24,
                overview = emptyList(),
                trend = emptyList(),
                allLogs = emptyList(),
                filters = emptyList(),
                loading = false,
                refreshing = false,
                error = null,
                partError = null
            )
        }
        if (zone != null) load(refreshing = false)
    }

    fun setGroupBy(groupBy: SecurityGroupBy) {
        if (_uiState.value.groupBy == groupBy) return
        _uiState.update { it.copy(groupBy = groupBy) }
        load(refreshing = false)
    }

    fun setTimeRange(range: SecurityTimeRange) {
        if (_uiState.value.timeRange == range) return
        _uiState.update { it.copy(timeRange = range) }
        load(refreshing = false)
    }

    /** 添加筛选器（值非空才生效）并重载 */
    fun addFilter(filter: SecurityFilter) {
        if (filter.value.isBlank()) return
        _uiState.update { it.copy(filters = it.filters + filter) }
        load(refreshing = false)
    }

    /** 移除指定筛选器并重载 */
    fun removeFilter(index: Int) {
        val list = _uiState.value.filters
        if (index !in list.indices) return
        _uiState.update { it.copy(filters = list.filterIndexed { i, _ -> i != index }) }
        load(refreshing = false)
    }

    fun clearFilters() {
        if (_uiState.value.filters.isEmpty()) return
        _uiState.update { it.copy(filters = emptyList()) }
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    fun load() = load(refreshing = false)

    private fun load(refreshing: Boolean) {
        val zone = _uiState.value.selectedZone ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = !refreshing, refreshing = refreshing, error = null, partError = null) }
            val failures = mutableListOf<String>()
            try {
                val range = _uiState.value.timeRange
                val gb = _uiState.value.groupBy
                val filters = _uiState.value.filters
                val result = coroutineScope {
                    val overview = async { runCatching { AppContainer.repository.getSecurityOverview(zone.id, range, gb, filters) } }
                    val trend = async { runCatching { AppContainer.repository.getSecurityTrend(zone.id, range, gb, filters) } }
                    val logs = async { runCatching { AppContainer.repository.getSecurityLogs(zone.id, range, filters) } }
                    Triple(overview, trend, logs)
                }
                val ov: List<SecuritySegment>? = result.first.await().getOrElse { e ->
                    failures.add("概况：${e.message ?: "加载失败"}"); null
                }
                val tr: List<SecurityTrendSeries>? = result.second.await().getOrElse { e ->
                    failures.add("趋势：${e.message ?: "加载失败"}"); null
                }
                val lg: List<SecurityLogEntry>? = result.third.await().getOrElse { e ->
                    failures.add("日志：${e.message ?: "加载失败"}"); null
                }
                _uiState.update {
                    it.copy(
                        overview = ov ?: emptyList(),
                        trend = tr ?: emptyList(),
                        allLogs = lg ?: emptyList(),
                        loading = false,
                        refreshing = false,
                        error = null,
                        partError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("；")
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, refreshing = false, error = e.message ?: "加载安全数据失败") }
            }
        }
    }
}