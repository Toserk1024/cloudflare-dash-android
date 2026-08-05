package com.java.myapplication.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.java.myapplication.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 初始化（Token 验证）界面状态 */
sealed interface OnboardingState {
    data object Idle : OnboardingState
    data object Loading : OnboardingState
    data class Error(val message: String) : OnboardingState
    data object Success : OnboardingState
}

class OnboardingViewModel : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state: StateFlow<OnboardingState> = _state

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword

    fun togglePasswordVisibility() {
        _showPassword.value = !_showPassword.value
    }

    /** 验证并保存 Token */
    fun verifyToken(token: String) {
        if (token.isBlank()) {
            _state.value = OnboardingState.Error("请输入 API Token")
            return
        }
        viewModelScope.launch {
            _state.value = OnboardingState.Loading
            try {
                val ok = AppContainer.repository.verifyToken(token.trim())
                if (ok) {
                    AppContainer.tokenStore.saveToken(token)
                    _state.value = OnboardingState.Success
                } else {
                    _state.value = OnboardingState.Error("Token 无效或已过期，请检查后重试")
                }
            } catch (e: Exception) {
                _state.value = OnboardingState.Error(e.message ?: "验证失败，请检查网络")
            }
        }
    }
}