package io.github.toserk1024.cfdash.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.AnalyticsRange
import io.github.toserk1024.cfdash.data.model.AnalyticsSum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 统计数据页 ViewModel（账号级汇总，支持 24h/7d/30d 切换） */
class StatsViewModel : ViewModel() {

    data class StatsUiState(
        val summary: AnalyticsSum? = null,
        val range: AnalyticsRange = AnalyticsRange.D7,
        val loading: Boolean = true,
        val error: String? = null
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

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                // 账号级统计：GET /accounts 取第一个账号的 accountTag（比 /user.accounts 更可靠）
                val accountId = AppContainer.repository.getAccounts().firstOrNull()?.id
                    ?: throw IllegalStateException("账号信息缺失，无法加载统计数据")
                val sum = AppContainer.repository.getAccountAnalytics(accountId, _uiState.value.range)
                _uiState.update { it.copy(summary = sum, loading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载统计失败") }
            }
        }
    }
}