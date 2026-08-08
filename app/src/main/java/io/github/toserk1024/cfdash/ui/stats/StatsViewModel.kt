package io.github.toserk1024.cfdash.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.BreakdownDimension
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 统计数据页 ViewModel（账号级汇总，支持 24h/7d/30d 切换） */
class StatsViewModel : ViewModel() {

    data class StatsUiState(
        val data: StatsData = StatsData(),
        val range: AnalyticsRange = AnalyticsRange.D7,
        val loading: Boolean = true,
        val error: String? = null,
        /** 部分图表区块加载失败的提示（如"趋势；国家/地区"），不阻塞其他区块 */
        val partError: String? = null
    )

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        load()
    }

    fun setRange(range: AnalyticsRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        load()
    }

    /**
     * 加载账号级统计数据：汇总必须成功（失败 → 整体错误页），
     * 趋势/维度分布/域名拆分并行加载且单项失败降级（该区块显示"加载失败"），不阻塞其他。
     */
    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, partError = null) }
            try {
                val accountId = AppContainer.repository.getAccounts().firstOrNull()?.id
                    ?: throw IllegalStateException("账号信息缺失，无法加载统计数据")
                val range = _uiState.value.range
                val failures = mutableListOf<String>()
                coroutineScope {
                    val summary = async { runCatching { AppContainer.repository.getAccountAnalytics(accountId, range) } }
                    val series = async { loadPart("趋势", failures) { AppContainer.repository.getAccountAnalyticsSeries(accountId, range) } }
                    val country = async { loadPart("国家/地区", failures) { AppContainer.repository.getAccountBreakdown(accountId, range, BreakdownDimension.COUNTRY) } }
                    val status = async { loadPart("状态码", failures) { AppContainer.repository.getAccountBreakdown(accountId, range, BreakdownDimension.STATUS) } }
                    val cache = async { loadPart("缓存", failures) { AppContainer.repository.getAccountBreakdown(accountId, range, BreakdownDimension.CACHE) } }
                    val zones = async { loadPart("域名拆分", failures) { AppContainer.repository.getAccountZoneBreakdown(accountId, range) } }
                    // 汇总失败 → 整体失败（其他 async 由 coroutineScope 取消）
                    val sum: AnalyticsSum = summary.await().getOrElse { throw it }
                    _uiState.update {
                        it.copy(
                            data = StatsData(
                                summary = sum,
                                series = series.await(),
                                country = country.await(),
                                status = status.await(),
                                cache = cache.await(),
                                zoneBreakdown = zones.await()
                            ),
                            loading = false,
                            error = null,
                            partError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("；")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载统计失败") }
            }
        }
    }

    /** 单项加载：失败时记录提示并返回 null（该区块降级显示） */
    private suspend fun <T> loadPart(label: String, failures: MutableList<String>, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            failures.add("$label：${e.message ?: "加载失败"}")
            null
        }
}