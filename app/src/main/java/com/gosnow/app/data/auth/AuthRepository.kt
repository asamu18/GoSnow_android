package com.gosnow.app.data.auth

import com.gosnow.app.datasupabase.CurrentUserStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.OTP
import io.github.jan.supabase.gotrue.user.UserInfo

class AuthRepository(private val supabaseClient: SupabaseClient) {

    suspend fun sendOtpToPhone(phone: String): Result<Unit> = runCatching {
        supabaseClient.auth.signInWith(OTP) {
            this.phone = phone
            createUser = true
        }
    }.map { }
        .mapErrorToUserMessage { mapSendOtpError(it) }

    suspend fun verifyOtpAndLogin(phone: String, code: String): Result<Unit> = runCatching {
        supabaseClient.auth.verifyPhoneOtp(
            phone = phone,
            token = code,
            type = OtpType.Phone.SMS
        )
        // 登录成功后再同步资料
        CurrentUserStore.refreshFromServer()
    }.map { }
        .mapErrorToUserMessage { mapVerifyOtpError(it) }

    suspend fun signOut(): Result<Unit> = runCatching {
        supabaseClient.auth.signOut()
    }

    suspend fun currentUser(): Result<UserInfo?> = runCatching {
        supabaseClient.auth.currentUserOrNull()
    }

    // ✅ 确保这里只有一个 hasActiveSession 函数
    suspend fun hasActiveSession(): Result<Boolean> = runCatching {
        // 尝试获取当前会话，如果不为空则说明已登录
        supabaseClient.auth.currentSessionOrNull() != null
    }

    // ------- 错误映射（核心） -------

    private fun mapSendOtpError(t: Throwable): String {
        val msg = t.message.orEmpty()
        val lower = msg.lowercase()

        return when {
            "rate" in lower || "too many" in lower || "429" in lower ->
                "发送太频繁了，请稍后再试"
            "phone" in lower && ("invalid" in lower || "format" in lower) ->
                "手机号格式不正确，请检查后重试"
            else ->
                "验证码发送失败，请稍后重试"
        }
    }

    private fun mapVerifyOtpError(t: Throwable): String {
        val msg = t.message.orEmpty()
        val lower = msg.lowercase()

        return when {
            ("token" in lower && "expired" in lower) || ("otp" in lower && "expired" in lower) ->
                "验证码已过期，请重新获取"
            ("token" in lower && "invalid" in lower) || ("otp" in lower && "invalid" in lower) ->
                "验证码不正确，请检查后重试"
            "used" in lower || "already" in lower ->
                "验证码已失效，请重新获取"
            "too many" in lower || "rate" in lower || "429" in lower ->
                "操作太频繁了，请稍后再试"
            else ->
                "登录失败，请稍后重试"
        }
    }
}

/** 把 Result.failure(Throwable) 统一替换成用户可读的 Throwable(message) */
private inline fun <T> Result<T>.mapErrorToUserMessage(
    mapper: (Throwable) -> String
): Result<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(IllegalStateException(mapper(it))) }
    )
}