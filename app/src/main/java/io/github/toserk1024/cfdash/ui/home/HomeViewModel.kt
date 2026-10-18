package io.github.toserk1024.cfdash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.User
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    private val repository: CloudflareRepository = AppContainer.repository
) : ViewModel() {

    data class HomeUiState(
        val user: User? = null,
        val loading: Boolean = true,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    /** 当前加载任务（新请求会取消旧请求，避免快切账号时旧响应覆盖新状态） */
    private var loadJob: Job? = null

    init {
        loadUser()
    }

    fun loadUser() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val user = repository.getUser()
                _uiState.update { it.copy(user = user, loading = false, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载用户信息失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 退出当前激活用户（多用户：仅删除当前用户，剩余用户保留） */
    fun logout() {
        AppContainer.tokenStore.getActiveUser()?.id?.let { AppContainer.tokenStore.deleteUser(it) }
    }
}