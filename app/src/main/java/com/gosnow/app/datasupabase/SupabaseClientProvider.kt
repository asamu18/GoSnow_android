package com.gosnow.app.datasupabase

import com.gosnow.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 负责创建并持有全局 SupabaseClient 实例。
 */
object SupabaseClientProvider {

    val supabaseClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)

            httpClient = HttpClient(Android) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = true
                        }
                    )
                }
            }
        }
    }
}
