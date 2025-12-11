package com.gosnow.app.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gosnow.app.data.auth.AuthRepository
import com.gosnow.app.datasupabase.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isCheckingSession: Boolean = true,
    val authMode: AuthMode = AuthMode.LOGIN
)

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            val hasSession = repository.hasActiveSession().getOrNull() == true
            _uiState.update {
                it.copy(
                    isLoggedIn = hasSession,
                    isCheckingSession = false,
                    errorMessage = null,
                    successMessage = null
                )
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value.trim(), errorMessage = null, successMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null, successMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, successMessage = null) }
    }

    fun switchMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                authMode = mode,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password

        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(errorMessage = "请输入有效的邮箱地址") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "密码长度至少 6 位") }
            return
        }

        if (state.authMode == AuthMode.REGISTER && password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "两次输入的密码不一致") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val result = if (state.authMode == AuthMode.LOGIN) {
                repository.signInWithEmail(email, password)
            } else {
                repository.signUpWithEmail(email, password)
            }

            result.onSuccess {
                handleAuthSuccess(state.authMode)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        errorMessage = throwable.message ?: "操作失败，请稍后重试",
                        isLoading = false,
                        isLoggedIn = false
                    )
                }
            }
        }
    }

    private suspend fun handleAuthSuccess(mode: AuthMode) {
        val user = repository.currentUser().getOrNull()
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoggedIn = user != null,
                successMessage = if (mode == AuthMode.REGISTER) "注册成功，已登录" else "登录成功",
                errorMessage = null
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.signOut()
            _uiState.update {
                it.copy(
                    isLoggedIn = false,
                    email = "",
                    password = "",
                    confirmPassword = "",
                    errorMessage = null,
                    successMessage = null
                )
            }
        }
    }

    companion object {
        fun provideFactory(@Suppress("UNUSED_PARAMETER") context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = AuthRepository(SupabaseClientProvider.supabaseClient)
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(repository) as T
                }
            }
    }
}
