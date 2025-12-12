package com.gosnow.app.datasupabase

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ProfileRepository {

    // 复用全局 SupabaseClient
    private val client get() = SupabaseClientProvider.supabaseClient

    // 你的头像 bucket 名，按你实际的来
    private const val AVATAR_BUCKET = "avatars"

    @Serializable
    private data class UserProfileUpdate(
        @SerialName("user_name")
        val userName: String,
        @SerialName("avatar_url")
        val avatarUrl: String?
    )

    /**
     * 更新昵称 + 头像
     *
     * @param nickname         新昵称
     * @param avatarBytes      新头像（压缩后的 ByteArray），null 表示没改头像
     * @param currentAvatarUrl 当前头像地址（没改头像时沿用）
     *
     * @return 最终生效的头像地址（可能为 null）
     */
    suspend fun updateProfile(
        nickname: String,
        avatarBytes: ByteArray?,
        currentAvatarUrl: String?
    ): String? = withContext(Dispatchers.IO) {
        val user = client.auth.currentUserOrNull()
            ?: throw IllegalStateException("未登录，无法更新资料")

        val userId = user.id  // Supabase auth 用户 id（String）

        var finalAvatarUrl = currentAvatarUrl

        // 1️⃣ 如果有新头像：上传到 Storage
        if (avatarBytes != null) {
            val bucket = client.storage.from(AVATAR_BUCKET)

            // 路径随便定义一个规则，你可以以后改
            val path = "user-$userId/avatar-${System.currentTimeMillis()}.jpg"

            // 上传（失败会抛异常）
            bucket.upload(path, avatarBytes)

            // 桶是 public 的话，这个就是公网 URL
            finalAvatarUrl = bucket.publicUrl(path)
        }

        // 2️⃣ 更新 Users 表里的昵称 + 头像
        // 这里使用「构建更新」的写法，不用 eq 过滤 DSL，避免版本差异的问题
        client.postgrest["Users"].update(
            UserProfileUpdate(
                userName = nickname,
                avatarUrl = finalAvatarUrl
            )
        )
        // ⚠️ 注意：
        // 这里没有写 where id = userId 的过滤条件，
        // 实际上依赖的是你在 Supabase / MemfireDB 上的 RLS 策略：
        // 一般会写「只允许 auth.uid() = id 的那一行被更新」。
        // 只要 RLS 配得正确，就只会更新当前用户这一行。

        return@withContext finalAvatarUrl
    }
}
