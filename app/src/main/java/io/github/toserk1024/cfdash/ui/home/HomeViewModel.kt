package io.github.toserk1024.cfdash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    data class HomeUiState(
        val user: User? = null,
        val loading: Boolean = true,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val user = AppContainer.repository.getUser()
                _uiState.update { it.copy(user = user, loading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "加载用户信息失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 退出登录：清除 Token 并返回初始化界面 */
    fun logout() {
        AppContainer.tokenStore.clear()
    }
}