package com.gosnow.app.ui.snowcircle.data.party

import com.gosnow.app.datasupabase.SupabaseClientProvider
import com.gosnow.app.ui.snowcircle.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PartyUserRepository {
    private val cache = mutableMapOf<String, User>()
    private val mutex = Mutex()
    private val client get() = SupabaseClientProvider.supabaseClient

    suspend fun getUserInfo(userId: String): User? = mutex.withLock {
        // 1. 查缓存
        if (cache.containsKey(userId)) return cache[userId]

        // 2. 查 Supabase (确保 RLS 允许读取 Users 表)
        return try {
            val user = client.from("Users")
                .select(columns = Columns.raw("id, user_name, avatar_url")) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<User>() // 假设你有 User 这个 @Serializable 类

            if (user != null) {
                cache[userId] = user
            }
            user
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}