package io.github.toserk1024.cfdash.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toserk1024.cfdash.AppContainer
import io.github.toserk1024.cfdash.data.api.AuthCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 认证方式（Global API Key 优先，API Token 其次） */
enum class AuthMode { GLOBAL, TOKEN }

/** 初始化（认证）界面状态 */
sealed interface OnboardingState {
    data object Idle : OnboardingState
    data object Loading : OnboardingState
    data class Error(val message: String) : OnboardingState
    data object Success : OnboardingState
}

class OnboardingViewModel : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state: StateFlow<OnboardingState> = _state

    // 默认 Global API Key（优先 global 其次 token）
    private val _authMode = MutableStateFlow(AuthMode.GLOBAL)
    val authMode: StateFlow<AuthMode> = _authMode

    private val _showPassword = MutableStateFlow(false)
    val showPassword: StateFlow<Boolean> = _showPassword

    fun setAuthMode(mode: AuthMode) {
        _authMode.value = mode
    }

    fun togglePasswordVisibility() {
        _showPassword.value = !_showPassword.value
    }

    /** 验证并保存 Global API Key */
    fun verifyGlobalKey(email: String, apiKey: String) {
        if (email.isBlank()) {
            _state.value = OnboardingState.Error("请输入邮箱")
            return
        }
        if (apiKey.isBlank()) {
            _state.value = OnboardingState.Error("请输入 Global API Key")
            return
        }
        verify(AuthCredential.GlobalKey(email.trim(), apiKey.trim())) {
            AppContainer.tokenStore.saveGlobalKey(it.email, it.apiKey)
        }
    }

    /** 验证并保存 API Token */
    fun verifyToken(token: String) {
        if (token.isBlank()) {
            _state.value = OnboardingState.Error("请输入 API Token")
            return
        }
        verify(AuthCredential.Token(token.trim())) {
            AppContainer.tokenStore.saveToken(it.value)
        }
    }

    /** 先验证后保存（验证时用 credentialOverride，不落盘） */
    private fun <C : AuthCredential> verify(credential: C, onSave: (C) -> Unit) {
        viewModelScope.launch {
            _state.value = OnboardingState.Loading
            try {
                val ok = AppContainer.repository.verifyCredential(credential)
                if (ok) {
                    onSave(credential)
                    _state.value = OnboardingState.Success
                } else {
                    _state.value = OnboardingState.Error("认证信息无效，请检查后重试")
                }
            } catch (e: Exception) {
                _state.value = OnboardingState.Error(e.message ?: "验证失败，请检查网络")
            }
        }
    }
}