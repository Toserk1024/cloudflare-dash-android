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

/**
 * 统计数据页 ViewModel（账号级汇总，支持 24h/7d/30d 切换）。
 * 全量缓存：按时间范围缓存 StatsData，首次加载/切换范围请求一次后走缓存，下拉刷新才重新请求。
 */
class StatsViewModel : ViewModel() {

    data class StatsUiState(
        val data: StatsData = StatsData(),
        val range: AnalyticsRange = AnalyticsRange.H24,
        val loading: Boolean = true,
        /** 下拉刷新中（保留旧数据展示，仅顶部指示器） */
        val refreshing: Boolean = false,
        val error: String? = null,
        /** 部分图表区块加载失败的提示（如"趋势；国家/地区"），不阻塞其他区块 */
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    /** 按时间范围的全量缓存（首次/切换加载后缓存，避免反复请求） */
    private val dataCache = mutableMapOf<AnalyticsRange, StatsData>()
    private val partErrorCache = mutableMapOf<AnalyticsRange, String?>()

    init {
        load()
    }

    fun setRange(range: AnalyticsRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        load()
    }

    /** 加载当前时间范围：优先命中缓存（避免重复请求），无缓存才请求 */
    fun load() {
        val range = _uiState.value.range
        dataCache[range]?.let { cached ->
            _uiState.update {
                it.copy(data = cached, loading = false, refreshing = false, error = null, partError = partErrorCache[range])
            }
            return
        }
        _uiState.update { it.copy(loading = true, refreshing = false, error = null, partError = null) }
        fetchAndCache(range)
    }

    /** 下拉刷新：强制重新请求当前时间范围并更新缓存 */
    fun refresh() {
        val range = _uiState.value.range
        _uiState.update { it.copy(refreshing = true, error = null, partError = null) }
        fetchAndCache(range, refreshing = true)
    }

    private fun fetchAndCache(range: AnalyticsRange, refreshing: Boolean = false) {
        viewModelScope.launch {
            try {
                val accountId = AppContainer.repository.getAccounts().firstOrNull()?.id
                    ?: throw IllegalStateException("账号信息缺失，无法加载统计数据")
                val result = fetchAll(accountId, range)
                dataCache[range] = result.data
                partErrorCache[range] = result.partError
                _uiState.update {
                    it.copy(
                        data = result.data,
                        loading = false,
                        refreshing = false,
                        error = null,
                        partError = result.partError
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, refreshing = false, error = e.message ?: "加载统计失败")
                }
            }
        }
    }

    /**
     * 并行拉取当前时间范围的全部统计：汇总必须成功（失败 → 整体错误），
     * 趋势/维度分布（国家+状态码）/域名拆分（仅 24h）单项失败降级。
     */
    private suspend fun fetchAll(accountId: String, range: AnalyticsRange): FetchResult {
        val failures = mutableListOf<String>()
        return coroutineScope {
            val summary = async { runCatching { AppContainer.repository.getAccountAnalytics(accountId, range) } }
            val series = async { loadPart("趋势", failures) { AppContainer.repository.getAccountAnalyticsSeries(accountId, range) } }
            val dist = async { loadPart("维度分布", failures) { AppContainer.repository.getAccountDistributions(accountId, range) } }
            // 域名拆分：AdaptiveGroups 仅支持 24h 范围，7d/30d 不请求
            val zones = async {
                if (range == AnalyticsRange.H24) {
                    loadPart("域名拆分", failures) { AppContainer.repository.getAccountZoneBreakdown(accountId, range) }
                } else {
                    null
                }
            }
            // 汇总失败 → 整体失败（其他 async 由 coroutineScope 取消）
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

    private data class FetchResult(val data: StatsData, val partError: String?)

    /** 单项加载：失败时记录提示并返回 null（该区块降级显示） */
    private suspend fun <T> loadPart(label: String, failures: MutableList<String>, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            failures.add("$label：${e.message ?: "加载失败"}")
            null
        }

    /** 缓存状态分布：Groups 无 cacheStatus 维度，由 cachedRequests/requests 计算"命中/未命中"两切片 */
    private fun buildCacheBreakdown(sum: AnalyticsSum): List<AnalyticsBreakdown> {
        val cached = sum.cachedRequests
        val uncached = (sum.requests - cached).coerceAtLeast(0)
        return listOf(
            AnalyticsBreakdown("命中", cached),
            AnalyticsBreakdown("未命中", uncached)
        ).filter { it.value > 0 }
    }
}