package io.github.toserk1024.cfdash.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.AccountRef
import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import io.github.toserk1024.cfdash.data.state.ZoneSelectionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
 * 多账号：账户列表缓存一次，支持切换统计账号（切换时清空缓存）。
 */
class StatsViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository
) : ViewModel() {

    data class StatsUiState(
        val data: StatsData = StatsData(),
        val range: AnalyticsRange = AnalyticsRange.H24,
        /** 默认账户级 */
        val mode: StatsMode = StatsMode.ACCOUNT,
        /** 账号列表（多账号时可切换） */
        val accounts: List<AccountRef> = emptyList(),
        /** 当前统计账号 id（null = 自动取第一个） */
        val accountId: String? = null,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    /** 当前域名级统计使用的 zoneId */
    private var currentZoneId: String? = null

    /** 账号列表内存缓存（只请求一次） */
    private var accountsCache: List<AccountRef> = emptyList()

    private var loadJob: Job? = null

    private val dataCache = mutableMapOf<String, StatsData>()
    private val partErrorCache = mutableMapOf<String, String?>()

    init {
        load()
        viewModelScope.launch {
            ZoneSelectionStore.selected.collect { zone -> setZone(zone?.id) }
        }
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

    /** 切换统计账号（账户级）：不同账号数据互不复用，直接清空缓存 */
    fun setAccount(accountId: String) {
        if (_uiState.value.accountId == accountId) return
        dataCache.clear()
        partErrorCache.clear()
        _uiState.update { it.copy(accountId = accountId) }
        if (_uiState.value.mode == StatsMode.ACCOUNT) load()
    }

    /** 设置域名级统计的选中域名（由全局单一事实源驱动） */
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
            loadJob?.cancel()
            _uiState.update { it.copy(data = StatsData(), loading = false, refreshing = false, error = "请先在右上角选择域名", partError = null) }
            return
        }

        val key = cacheKey(mode, range, zoneId)
        dataCache[key]?.let { cached ->
            loadJob?.cancel()
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
        fetchAndCache(key, mode, range, zoneId)
    }

    private fun fetchAndCache(
        key: String,
        mode: StatsMode,
        range: AnalyticsRange,
        zoneId: String?
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val result = when (mode) {
                    StatsMode.ACCOUNT -> fetchAllAccount(resolveAccountId(), range)
                    StatsMode.ZONE -> fetchAllZone(
                        zoneId ?: throw IllegalStateException("请先选择域名"),
                        range
                    )
                }
                dataCache[key] = result.data
                partErrorCache[key] = result.partError
                _uiState.update {
                    it.copy(data = result.data, loading = false, refreshing = false, error = null, partError = result.partError)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, refreshing = false, error = e.message ?: "加载统计失败") }
            }
        }
    }

    /** 解析当前统计账号：已选则用之，否则取账号列表第一个并回写 */
    private suspend fun resolveAccountId(): String {
        _uiState.value.accountId?.let { return it }
        val accounts = ensureAccounts()
        val first = accounts.firstOrNull()?.id
            ?: throw IllegalStateException("账号信息缺失，无法加载统计数据")
        _uiState.update { it.copy(accountId = first) }
        return first
    }

    /** 账号列表只拉一次（避免每次刷新统计都重复请求 /accounts） */
    private suspend fun ensureAccounts(): List<AccountRef> {
        if (accountsCache.isNotEmpty()) return accountsCache
        val list = repository.getAccounts()
        accountsCache = list
        _uiState.update { it.copy(accounts = list) }
        return list
    }

    /** 账户级并行拉取：汇总 + 趋势 + 维度 + 域名拆分（仅 24h） */
    private suspend fun fetchAllAccount(accountId: String, range: AnalyticsRange): FetchResult {
        val failures = mutableListOf<String>()
        return coroutineScope {
            val summary = async { runCatching { repository.getAccountAnalytics(accountId, range) } }
            val series = async { loadPart("趋势", failures) { repository.getAccountAnalyticsSeries(accountId, range) } }
            val dist = async { loadPart("维度分布", failures) { repository.getAccountDistributions(accountId, range) } }
            val zones = async {
                if (range == AnalyticsRange.H24) {
                    loadPart("域名拆分", failures) { repository.getAccountZoneBreakdown(accountId, range) }
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
            val summary = async { runCatching { repository.getZoneAnalytics(zoneId, range) } }
            val series = async { loadPart("趋势", failures) { repository.getZoneAnalyticsSeries(zoneId, range) } }
            val dist = async { loadPart("维度分布", failures) { repository.getZoneDistributions(zoneId, range) } }
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
        } catch (e: CancellationException) {
            throw e
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