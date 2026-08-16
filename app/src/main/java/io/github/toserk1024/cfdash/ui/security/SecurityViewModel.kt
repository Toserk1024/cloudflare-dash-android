package io.github.toserk1024.cfdash.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.SecurityBreakdownItem
import io.github.toserk1024.cfdash.data.model.SecurityGroupBy
import io.github.toserk1024.cfdash.data.model.SecurityLogEntry
import io.github.toserk1024.cfdash.data.model.SecurityOverview
import io.github.toserk1024.cfdash.data.model.SecurityTrendPoint
import io.github.toserk1024.cfdash.data.model.Zone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 安全分析 ViewModel。
 * 当前域名由全局域名选择器（HomeScreen 的 ZoneViewModel）传入。
 * 概况（回源/命中/缓解）+ 24h 趋势来自 httpRequests1hGroups + firewallEventsAdaptiveGroups；
 * 分组分布（国家/设备/IP/HTTP版本/缓存状态 或 操作/来源）按 SecurityGroupBy 加载；
 * 日志来自 firewallEventsAdaptive，由筛选器（操作/来源/国家）客户端过滤。
 * 单项加载失败不阻塞其他（partError 顶部提示）。
 */
class SecurityViewModel : ViewModel() {

    data class SecurityUiState(
        val selectedZone: Zone? = null,
        /** 分组视图方式 */
        val groupBy: SecurityGroupBy = SecurityGroupBy.ALL,
        /** 安全概况：回源/命中/缓解（24h） */
        val overview: SecurityOverview? = null,
        /** 24h 趋势（按小时：总请求 + 缓解） */
        val trend: List<SecurityTrendPoint> = emptyList(),
        /** 分组分布（groupBy != ALL 时加载） */
        val breakdown: List<SecurityBreakdownItem> = emptyList(),
        /** 全量日志（未过滤，供筛选器派生候选与过滤） */
        val allLogs: List<SecurityLogEntry> = emptyList(),
        /** 筛选器 */
        val actionFilter: String? = null,
        val sourceFilter: String? = null,
        val countryFilter: String? = null,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val breakdownLoading: Boolean = false,
        val error: String? = null,
        val partError: String? = null
    ) {
        /** 按筛选器过滤后的日志 */
        val logs: List<SecurityLogEntry> get() = allLogs.filter { log ->
            actionFilter?.let { log.action == it } != false &&
                sourceFilter?.let { log.source == it } != false &&
                countryFilter?.let { log.clientCountry == it } != false
        }
        val availableActions: List<String> get() = allLogs.map { it.action }.distinct().filter { it.isNotBlank() }
        val availableSources: List<String> get() = allLogs.mapNotNull { it.source }.distinct().filter { it.isNotBlank() }
        val availableCountries: List<String> get() = allLogs.mapNotNull { it.clientCountry }.distinct().filter { it.isNotBlank() }
    }

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState

    /** 由 HomeScreen 响应全局选中域名变化时调用：切换域名即重载全部安全数据 */
    fun setZone(zone: Zone?) {
        _uiState.update {
            it.copy(
                selectedZone = zone,
                groupBy = SecurityGroupBy.ALL,
                overview = null,
                trend = emptyList(),
                breakdown = emptyList(),
                allLogs = emptyList(),
                actionFilter = null,
                sourceFilter = null,
                countryFilter = null,
                loading = false,
                refreshing = false,
                error = null,
                partError = null
            )
        }
        if (zone != null) load(zone.id, refreshing = false)
    }

    /** 切换分组视图方式（概况/趋势不变，仅重载分组分布；groupBy=ALL 清空分布） */
    fun setGroupBy(groupBy: SecurityGroupBy) {
        if (_uiState.value.groupBy == groupBy) return
        _uiState.update { it.copy(groupBy = groupBy, breakdown = emptyList()) }
        val zone = _uiState.value.selectedZone ?: return
        if (groupBy == SecurityGroupBy.ALL) return
        loadBreakdown(zone.id, groupBy)
    }

    fun setActionFilter(v: String?) = _uiState.update { it.copy(actionFilter = v) }
    fun setSourceFilter(v: String?) = _uiState.update { it.copy(sourceFilter = v) }
    fun setCountryFilter(v: String?) = _uiState.update { it.copy(countryFilter = v) }

    fun refresh() {
        val zone = _uiState.value.selectedZone ?: return
        _uiState.update { it.copy(refreshing = true, error = null, partError = null) }
        load(zone.id, refreshing = true)
    }

    fun load() {
        val zone = _uiState.value.selectedZone ?: return
        load(zone.id, refreshing = false)
    }

    private fun load(zoneId: String, refreshing: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = !refreshing, refreshing = refreshing, error = null, partError = null) }
            val failures = mutableListOf<String>()
            try {
                val result = coroutineScope {
                    val http = async { runCatching { AppContainer.repository.getHttpSecurity(zoneId) } }
                    val miti = async { runCatching { AppContainer.repository.getMitigation(zoneId) } }
                    val logs = async { runCatching { AppContainer.repository.getSecurityLogs(zoneId) } }
                    val breakdown = async {
                        if (_uiState.value.groupBy != SecurityGroupBy.ALL) {
                            loadBreakdownPart(zoneId, _uiState.value.groupBy, failures)
                        } else null
                    }
                    Triple(http, miti, logs) to breakdown
                }
                val (http, miti, logs) = result.first
                val httpData: Pair<List<SecurityTrendPoint>, SecurityOverview>? = http.await().getOrElse { e ->
                    failures.add("概况：${e.message ?: "加载失败"}"); null
                }
                val mitiData: Pair<List<SecurityTrendPoint>, Long>? = miti.await().getOrElse { e ->
                    failures.add("缓解：${e.message ?: "加载失败"}"); null
                }
                val logList: List<SecurityLogEntry>? = logs.await().getOrElse { e ->
                    failures.add("日志：${e.message ?: "加载失败"}"); null
                }

                val overview = buildOverview(httpData, mitiData?.second)
                val trend = mergeTrend(httpData?.first, mitiData?.first)

                _uiState.update {
                    it.copy(
                        overview = overview,
                        trend = trend,
                        allLogs = logList ?: emptyList(),
                        breakdown = result.second ?: it.breakdown,
                        loading = false,
                        refreshing = false,
                        error = null,
                        partError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("；")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, refreshing = false, error = e.message ?: "加载安全数据失败")
                }
            }
        }
    }

    private fun loadBreakdown(zoneId: String, groupBy: SecurityGroupBy) {
        viewModelScope.launch {
            _uiState.update { it.copy(breakdownLoading = true) }
            try {
                val items = AppContainer.repository.getSecurityBreakdown(zoneId, groupBy)
                _uiState.update { it.copy(breakdown = items, breakdownLoading = false, partError = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(breakdown = emptyList(), breakdownLoading = false, partError = "分组：${e.message ?: "加载失败"}")
                }
            }
        }
    }

    private suspend fun loadBreakdownPart(zoneId: String, groupBy: SecurityGroupBy, failures: MutableList<String>): List<SecurityBreakdownItem>? =
        try {
            AppContainer.repository.getSecurityBreakdown(zoneId, groupBy)
        } catch (e: Exception) {
            failures.add("分组：${e.message ?: "加载失败"}")
            null
        }

    /** 组装概况：origin = 总请求 - 命中 - 缓解 */
    private fun buildOverview(http: Pair<List<SecurityTrendPoint>, SecurityOverview>?, mitigated: Long?): SecurityOverview? {
        if (http == null) return null
        val requests = http.second.origin + http.second.cached
        val m = mitigated ?: 0L
        return SecurityOverview(
            origin = (requests - http.second.cached - m).coerceAtLeast(0),
            cached = http.second.cached,
            mitigated = m
        )
    }

    /** 按小时 label 合并 HTTP 趋势（requests）与缓解趋势（mitigated） */
    private fun mergeTrend(http: List<SecurityTrendPoint>?, miti: List<SecurityTrendPoint>?): List<SecurityTrendPoint> {
        val byLabel = LinkedHashMap<String, SecurityTrendPoint>()
        http?.forEach { p -> byLabel[p.label] = SecurityTrendPoint(p.label, p.requests, 0) }
        miti?.forEach { p ->
            val cur = byLabel[p.label] ?: SecurityTrendPoint(p.label, 0, 0)
            byLabel[p.label] = cur.copy(mitigated = cur.mitigated + p.mitigated)
        }
        return byLabel.values.sortedBy { it.label }
    }
}