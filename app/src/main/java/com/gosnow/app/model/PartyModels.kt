package com.gosnow.app.ui.snowcircle.model

import kotlinx.serialization.Serializable

@Serializable
data class PartyLocationMessage(
    val user_id: String,
    val lat: Double,
    val lon: Double,
    val avatar_url: String? = null
)

data class PartyMember(
    val userId: String,
    val lat: Double,
    val lon: Double,
    val avatarUrl: String?,
    val userName: String? = null,
    val lastUpdate: Long = System.currentTimeMillis()
)

sealed class PartyState {
    data object Idle : PartyState()
    data class Joined(
        val code: String,
        val members: List<PartyMember>,
        val isHost: Boolean = false // ✅ 新增：标识当前用户是否为队长
    ) : PartyState()
}
@Serializable
data class MemberPresence(
    val user_id: String,
    val lat: Double,
    val lon: Double
)