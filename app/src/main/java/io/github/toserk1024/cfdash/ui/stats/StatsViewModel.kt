package io.github.toserk1024.cfdash.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 统计数据模式：账户级 / 域名级 */
enum class StatsMode { ACCOUNT, ZONE }

/**
 * 统计数据页 ViewModel（账户级 / 域名级，支持 24h/7d/30d 切换）。
 * 全量缓存：按 模式-时间范围（域名级含 zoneId）缓存 StatsData，切换/加载一次后走缓存，下拉刷新才重新请求。
 */
class StatsViewModel : ViewModel() {

    data class StatsUiState(
        val data: StatsData = StatsData(),
        val range: AnalyticsRange = AnalyticsRange.H24,
        /** 默认账户级 */
        val mode: StatsMode = StatsMode.ACCOUNT,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    /** 当前域名级统计使用的 zoneId */
    private var currentZoneId: String? = null

    private val dataCache = mutableMapOf<String, StatsData>()
    private val partErrorCache = mutableMapOf<String, String?>()

    init {
        load()
    }

    fun setRange(range: AnalyticsRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        load()
    }

    /** 切换账户级 / 域名级 */
    fun setMode(mode: StatsMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode) }
        load()
    }

    /** 设置域名级统计的选中域名（由 HomeScreen 响应全局选中域名变化时调用） */
    fun setZone(zoneId: String?) {
        if (currentZoneId == zoneId) return
        currentZoneId = zoneId
        if (_uiState.value.mode == StatsMode.ZONE) load()
    }

    private fun cacheKey(mode: StatsMode, range: AnalyticsRange, zoneId: String?) =
        "${mode.name}-${range.name}-${zoneId.orEmpty()}"

    fun load() {
        val range = _uiState.value.range
        val mode = _uiState.value.mode
        val zoneId = if (mode == StatsMode.ZONE) currentZoneId else null

        // 域名级未选域名 → 提示
        if (mode == StatsMode.ZONE && zoneId.isNullOrBlank()) {
            _uiState.update { it.copy(data = StatsData(), loading = false, refreshing = false, error = "请先在右上角选择域名", partError = null) }
            return
        }

        val key = cacheKey(mode, range, zoneId)
        dataCache[key]?.let { cached ->
            _uiState.update {
                it.copy(data = cached, loading = false, refreshing = false, error = null, partError = partErrorCache[key])
            }
            return
        }
        _uiState.update { it.copy(loading = true, refreshing = false, error = null, partError = null) }
        fetchAndCache(key, mode, range, zoneId)
    }

    fun refresh() {
        val range = _uiState.value.range
        val mode = _uiState.value.mode
        val zoneId = if (mode == StatsMode.ZONE) currentZoneId else null
        if (mode == StatsMode.ZONE && zoneId.isNullOrBlank()) {
            _uiState.update { it.copy(refreshing = false, error = "请先选择域名") }
            return
        }
        val key = cacheKey(mode, range, zoneId)
        _uiState.update { it.copy(refreshing = true, error = null, partError = null) }
        fetchAndCache(key, mode, range, zoneId, refreshing = true)
    }

    private fun fetchAndCache(key: String, mode: StatsMode, range: AnalyticsRange, zoneId: String?, refreshing: Boolean = false) {
        viewModelScope.launch {
            try {
                val result = when (mode) {
                    StatsMode.ACCOUNT -> {
                        val accountId = AppContainer.repository.getAccounts().firstOrNull()?.id
                            ?: throw IllegalStateException("账号信息缺失，无法加载统计数据")
                        fetchAllAccount(accountId, range)
                    }
                    StatsMode.ZONE -> {
                        val zid = zoneId ?: throw IllegalStateException("请先选择域名")
                        fetchAllZone(zid, range)
                    }
                }
                dataCache[key] = result.data
                partErrorCache[key] = result.partError
                _uiState.update {
                    it.copy(data = result.data, loading = false, refreshing = false, error = null, partError = result.partError)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, refreshing = false, error = e.message ?: "加载统计失败") }
            }
        }
    }

    /** 账户级并行拉取：汇总 + 趋势 + 维度 + 域名拆分（仅 24h） */
    private suspend fun fetchAllAccount(accountId: String, range: AnalyticsRange): FetchResult {
        val failures = mutableListOf<String>()
        return coroutineScope {
            val summary = async { runCatching { AppContainer.repository.getAccountAnalytics(accountId, range) } }
            val series = async { loadPart("趋势", failures) { AppContainer.repository.getAccountAnalyticsSeries(accountId, range) } }
            val dist = async { loadPart("维度分布", failures) { AppContainer.repository.getAccountDistributions(accountId, range) } }
            val zones = async {
                if (range == AnalyticsRange.H24) {
                    loadPart("域名拆分", failures) { AppContainer.repository.getAccountZoneBreakdown(accountId, range) }
                } else null
            }
            val sum: AnalyticsSum = summary.await().getOrElse { throw it }
            val distributions = dist.await()
            FetchResult(
                data = StatsData(
                    summary = sum,
                    series = series.await(),
                    country = distributions?.country,
                    status = distributions?.status,
                    cache = buildCacheBreakdown(sum),
                    zoneBreakdown = zones.await()
                ),
                partError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("；")
            )
        }
    }

    /** 域名级并行拉取：汇总 + 趋势 + 维度（无域名拆分） */
    private suspend fun fetchAllZone(zoneId: String, range: AnalyticsRange): FetchResult {
        val failures = mutableListOf<String>()
        return coroutineScope {
            val summary = async { runCatching { AppContainer.repository.getZoneAnalytics(zoneId, range) } }
            val series = async { loadPart("趋势", failures) { AppContainer.repository.getZoneAnalyticsSeries(zoneId, range) } }
            val dist = async { loadPart("维度分布", failures) { AppContainer.repository.getZoneDistributions(zoneId, range) } }
            val sum: AnalyticsSum = summary.await().getOrElse { throw it }
            val distributions = dist.await()
            FetchResult(
                data = StatsData(
                    summary = sum,
                    series = series.await(),
                    country = distributions?.country,
                    status = distributions?.status,
                    cache = buildCacheBreakdown(sum)
                ),
                partError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("；")
            )
        }
    }

    private data class FetchResult(val data: StatsData, val partError: String?)

    private suspend fun <T> loadPart(label: String, failures: MutableList<String>, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            failures.add("$label：${e.message ?: "加载失败"}")
            null
        }

    private fun buildCacheBreakdown(sum: AnalyticsSum): List<AnalyticsBreakdown> {
        val cached = sum.cachedRequests
        val uncached = (sum.requests - cached).coerceAtLeast(0)
        return listOf(
            AnalyticsBreakdown("命中", cached),
            AnalyticsBreakdown("未命中", uncached)
        ).filter { it.value > 0 }
    }
}