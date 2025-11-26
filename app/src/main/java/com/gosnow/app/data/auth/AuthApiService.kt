package com.gosnow.app.data.auth

import com.gosnow.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthApiService(
    private val client: OkHttpClient = OkHttpClient()
) {

    class AuthApiException(message: String) : Exception(message)

    suspend fun login(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
        }.toString()

        performTokenRequest(
            url = "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password",
            payload = payload
        ) { json, fallbackEmail ->
            val emailFromServer = json.optJSONObject("user")?.optString("email") ?: fallbackEmail
            json.extractSession(email = emailFromServer)
        }
    }

    suspend fun sendSmsCode(phone: String) = withContext(Dispatchers.IO) {
        ensureSupabaseConfigured()

        val payload = JSONObject().apply {
            put("phone", phone)
            put("channel", "sms")
            put("create_user", true)
        }.toString()

        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL}/auth/v1/otp")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            val message = errorBody?.let { parseErrorMessage(it) }
                ?: "验证码发送失败（${response.code}）"
            throw AuthApiException(message)
        }
    }

    suspend fun loginWithSms(phone: String, code: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("phone", phone)
            put("token", code)
            put("type", "sms")
        }.toString()

        performTokenRequest(
            url = "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=otp",
            payload = payload
        ) { json, _ ->
            json.extractSession(phone)
        }
    }

    private fun ensureSupabaseConfigured() {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw AuthApiException("Supabase 配置缺失，请在 local.properties 或环境变量中设置 SUPABASE_URL 与 SUPABASE_ANON_KEY")
        }
    }

    private fun parseErrorMessage(body: String): String? {
        return try {
            val json = JSONObject(body)
            json.optString("error_description")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error") }
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.extractSession(email: String? = null): AuthSession {
        val accessToken = optString("access_token")
        val refreshToken = optString("refresh_token")
        val user = optJSONObject("user")
        val userId = user?.optString("id") ?: ""
        val accountEmail = email ?: user?.optString("email")

        if (accessToken.isBlank() || userId.isBlank()) {
            throw AuthApiException("登录响应不完整，请稍后重试")
        }

        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = accountEmail ?: ""
        )
    }

    private suspend fun performTokenRequest(
        url: String,
        payload: String,
        onSuccess: (JSONObject, fallbackEmail: String) -> AuthSession
    ): AuthSession {
        ensureSupabaseConfigured()

        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            val message = errorBody?.let { parseErrorMessage(it) }
                ?: "登录失败（${response.code}）"
            throw AuthApiException(message)
        }

        val responseBody = response.body?.string()
            ?: throw AuthApiException("登录失败：服务器返回空响应")

        val json = JSONObject(responseBody)
        return onSuccess(json, "")
    }
}
