package com.gosnow.app.datasupabase

import android.content.Context
import com.gosnow.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime // ✅ 新增导入
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.cio.CIO // ✅ 新增
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

object SupabaseClientProvider {

    @Volatile
    private var _client: SupabaseClient? = null

    val supabaseClient: SupabaseClient
        get() = requireNotNull(_client) {
            "SupabaseClientProvider not initialized. Call SupabaseClientProvider.init(context) in Application.onCreate()."
        }

    @OptIn(SupabaseInternal::class)
    fun init(context: Context) {
        if (_client != null) return
        synchronized(this) {
            if (_client != null) return

            _client = createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            ) {
                install(Auth) {
                    autoLoadFromStorage = true
                    alwaysAutoRefresh = true
                }
                install(Postgrest)
                install(Storage)

                // ✅ 核心修复：安装 Realtime 插件
                install(Realtime) {
                    // ✅ 大胆尝试：延长重连时间，并显式指定心跳
                    heartbeatInterval = 20.seconds
                    // 强制每次连接都尝试清理之前的残余 Session
                    disconnectOnSessionLoss = true
                }

                httpConfig {
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                isLenient = true
                                explicitNulls = false
                            }
                        )
                    }
                    defaultRequest {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                    }
                }

                // ✅ 核心修改：将 httpEngine 改为 CIO
                httpEngine = CIO.create()
            }
        }
    }
}