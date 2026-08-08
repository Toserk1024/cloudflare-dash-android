package io.github.toserk1024.cfdash.ui.zones

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.AnalyticsBreakdown
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import io.github.toserk1024.cfdash.data.model.Zone
import io.github.toserk1024.cfdash.data.model.ZoneSetting
import io.github.toserk1024.cfdash.ui.stats.StatsData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZoneDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val zoneId: String = checkNotNull(savedStateHandle["zoneId"])

    data class ZoneDetailUiState(
        val zone: Zone? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val deleting: Boolean = false,
        val deleted: Boolean = false,
        // ===== 高级设置（null = 未加载/加载失败）=====
        val devMode: Boolean? = null,
        val devModeRemaining: Long = 0,
        val underAttack: Boolean? = null,
        val ipv6: Boolean? = null,
        // 正在切换的 setting 名（防连点，非 null 时禁用全部开关）
        val settingsBusy: String? = null,
        val settingsError: String? = null,
        // ===== 域名级统计 =====
        val statsData: StatsData = StatsData(),
        val statsRange: AnalyticsRange = AnalyticsRange.D7,
        val statsLoading: Boolean = false,
        val statsError: String? = null,
        val statsPartError: String? = null
    )

    private val _uiState = MutableStateFlow(ZoneDetailUiState())
    val uiState: StateFlow<ZoneDetailUiState> = _uiState

    /** 域名级统计缓存（按时间范围，切换不重复请求） */
    private val statsCache = mutableMapOf<AnalyticsRange, StatsData>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, settingsError = null) }
            // 域名详情、高级设置、域名统计并发请求（页面 loading 动画期间即开始）
            coroutineScope {
                async {
                    runCatching { AppContainer.repository.getZone(zoneId) }
                        .onSuccess { z -> _uiState.update { it.copy(zone = z, loading = false, error = null) } }
                        .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message) } }
                }
                async { loadSettings() }
                async { loadStats() }
            }
        }
    }

    /** 并行加载三个高级设置（单项失败不阻塞其他，失败原因写入 settingsError） */
    private suspend fun loadSettings() {
        coroutineScope {
            async {
                runCatching { AppContainer.repository.getZoneSetting(zoneId, "development_mode") }
                    .onSuccess { s ->
                        _uiState.update { it.copy(devMode = s.value == "on", devModeRemaining = s.time_remaining) }
                    }
                    .onFailure { e -> _uiState.update { it.copy(settingsError = e.message) } }
            }
            async {
                runCatching { AppContainer.repository.getZoneSetting(zoneId, "security_level") }
                    .onSuccess { s ->
                        _uiState.update { it.copy(underAttack = s.value == "under_attack") }
                    }
                    .onFailure { e -> _uiState.update { it.copy(settingsError = e.message) } }
            }
            async {
                runCatching { AppContainer.repository.getZoneSetting(zoneId, "ipv6") }
                    .onSuccess { s ->
                        _uiState.update { it.copy(ipv6 = s.value == "on") }
                    }
                    .onFailure { e -> _uiState.update { it.copy(settingsError = e.message) } }
            }
        }
    }
    // ===== 高级设置切换 =====

    /** 仅重试高级设置加载（页面不重新 loading） */
    fun refreshSettings() {
        _uiState.update { it.copy(settingsError = null) }
        viewModelScope.launch { loadSettings() }
    }

    // ===== 域名级统计 =====

    /** 重试域名级统计加载（强制重新请求并更新缓存） */
    fun refreshStats() {
        _uiState.update { it.copy(statsError = null) }
        viewModelScope.launch { loadStats(force = true) }
    }

    /** 切换统计时间范围并重新拉取 */
    fun setStatsRange(range: AnalyticsRange) {
        if (_uiState.value.statsRange == range) return
        _uiState.update { it.copy(statsRange = range) }
        viewModelScope.launch { loadStats() }
    }

    /** 加载域名级统计（不阻塞页面主内容；命中缓存直接展示，force=true 时重新请求；汇总必须成功，趋势/维度分布单项失败降级） */
    private suspend fun loadStats(force: Boolean = false) {
        val range = _uiState.value.statsRange
        if (!force) {
            statsCache[range]?.let { cached ->
                _uiState.update { it.copy(statsData = cached, statsLoading = false, statsError = null, statsPartError = null) }
                return
            }
        }
        _uiState.update { it.copy(statsLoading = true, statsError = null, statsPartError = null) }
        try {
            val failures = mutableListOf<String>()
            coroutineScope {
                val summary = async { runCatching { AppContainer.repository.getZoneAnalytics(zoneId, range) } }
                val series = async { loadStatsPart("趋势", failures) { AppContainer.repository.getZoneAnalyticsSeries(zoneId, range) } }
                val dist = async { loadStatsPart("维度分布", failures) { AppContainer.repository.getZoneDistributions(zoneId, range) } }
                // 汇总失败 → 统计整体失败（其他 async 由 coroutineScope 取消）
                val sum = summary.await().getOrElse { throw it }
                val distributions = dist.await()
                val statsData = StatsData(
                    summary = sum,
                    series = series.await(),
                    country = distributions?.country,
                    status = distributions?.status,
                    cache = buildCacheBreakdown(sum)
                )
                statsCache[range] = statsData
                _uiState.update {
                    it.copy(
                        statsData = statsData,
                        statsLoading = false,
                        statsError = null,
                        statsPartError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("；")
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(statsLoading = false, statsError = e.message ?: "加载统计失败") }
        }
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

    /** 统计单项加载：失败时记录提示并返回 null（该区块降级显示） */
    private suspend fun <T> loadStatsPart(label: String, failures: MutableList<String>, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            failures.add("$label：${e.message ?: "加载失败"}")
            null
        }

    fun setDevelopmentMode(on: Boolean) =
        updateSetting("development_mode", if (on) "on" else "off") { s ->
            _uiState.update { it.copy(devMode = s.value == "on", devModeRemaining = s.time_remaining) }
        }

    fun setUnderAttack(on: Boolean) =
        updateSetting("security_level", if (on) "under_attack" else "medium") { s ->
            _uiState.update { it.copy(underAttack = s.value == "under_attack") }
        }

    fun setIpv6(on: Boolean) =
        updateSetting("ipv6", if (on) "on" else "off") { s ->
            _uiState.update { it.copy(ipv6 = s.value == "on") }
        }

    /** 通用设置更新：切换中防连点，成功后回调更新状态，失败保留原值并提示 */
    private fun updateSetting(setting: String, value: String, onSuccess: (ZoneSetting) -> Unit) {
        if (_uiState.value.settingsBusy != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(settingsBusy = setting, settingsError = null) }
            try {
                val s = AppContainer.repository.updateZoneSetting(zoneId, setting, value)
                onSuccess(s)
            } catch (e: Exception) {
                _uiState.update { it.copy(settingsError = e.message) }
            } finally {
                _uiState.update { it.copy(settingsBusy = null) }
            }
        }
    }

    fun deleteZone() {
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true) }
            try {
                AppContainer.repository.deleteZone(zoneId)
                _uiState.update { it.copy(deleting = false, deleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false, error = e.message) }
            }
        }
    }
}