package com.gosnow.app.datasupabase

import com.gosnow.app.BuildConfig
import io.github.jan.supabase.SupabaseClient

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android

/**
 * 负责创建并持有全局 SupabaseClient 实例。
 */
object SupabaseClientProvider {

    val supabaseClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            // 按需开启模块
            install(Auth)
            install(Postgrest)
            install(Storage)

            // 2.4.0 正确的 Ktor 配置方式：用 httpEngine，而不是 httpClient
            httpEngine = Android.create()
        }
    }
}
