package com.gosnow.app.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gosnow.app.datasupabase.SupabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 和 iOS 保持一致：手机号 11 位 + 验证码 6 位
private const val MIN_PHONE_LENGTH = 11
private const val MIN_CODE_LENGTH = 6

data class LoginUiState(
    val phoneNumber: String = "",
    val verificationCode: String = "",
    val isSendingCode: Boolean = false,
    val isVerifyingCode: Boolean = false,
    val codeSent: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val isCheckingSession: Boolean = true
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        checkExistingSession()
    }

    /**
     * 启动时检查 Supabase 是否已有登录会话
     */
    private fun checkExistingSession() {
        viewModelScope.launch {
            try {
                val user = SupabaseManager.getCurrentUser()
                _uiState.update {
                    it.copy(
                        isLoggedIn = user != null,
                        isCheckingSession = false,
                        errorMessage = null
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        isCheckingSession = false
                    )
                }
            }
        }
    }

    fun onPhoneChange(value: String) {
        _uiState.update {
            it.copy(
                phoneNumber = value.trim(),
                errorMessage = null,
                codeSent = false
            )
        }
    }

    fun onVerificationCodeChange(value: String) {
        _uiState.update {
            it.copy(
                verificationCode = value.trim(),
                errorMessage = null
            )
        }
    }

    /**
     * 发送验证码
     */
    fun sendVerificationCode() {
        val phone = _uiState.value.phoneNumber.filter { it.isDigit() }

        if (phone.length < MIN_PHONE_LENGTH) {
            _uiState.update { it.copy(errorMessage = "请输入 11 位手机号") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingCode = true, errorMessage = null) }
            try {
                SupabaseManager.sendLoginOtp(phone)
                _uiState.update { it.copy(codeSent = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "验证码发送失败，请稍后重试",
                        codeSent = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isSendingCode = false) }
            }
        }
    }

    /**
     * 校验验证码并登录
     */
    fun verifyCodeAndLogin() {
        val phone = _uiState.value.phoneNumber.filter { it.isDigit() }
        val code = _uiState.value.verificationCode.filter { it.isDigit() }

        if (phone.length < MIN_PHONE_LENGTH) {
            _uiState.update { it.copy(errorMessage = "请输入 11 位手机号") }
            return
        }

        if (code.length < MIN_CODE_LENGTH) {
            _uiState.update { it.copy(errorMessage = "请输入 6 位短信验证码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingCode = true, errorMessage = null) }
            try {
                SupabaseManager.verifyLoginOtp(phone, code)

                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message ?: "登录失败，请稍后重试",
                        isLoggedIn = false
                    )
                }
            } finally {
                _uiState.update { it.copy(isVerifyingCode = false) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                SupabaseManager.logout()
            } catch (_: Exception) {
                // 即使 signOut 报错，也尽量在本地清一下状态
            } finally {
                _uiState.update {
                    it.copy(
                        phoneNumber = "",
                        verificationCode = "",
                        codeSent = false,
                        isLoggedIn = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(@Suppress("UNUSED_PARAMETER") context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LoginViewModel() as T
                }
            }
    }
}
