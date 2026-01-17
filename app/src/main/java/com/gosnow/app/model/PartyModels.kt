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
    val userName: String? = null, // 本地查询后填充
    val lastUpdate: Long = System.currentTimeMillis()
)

sealed class PartyState {
    data object Idle : PartyState()
    data class Joined(val code: String, val members: List<PartyMember>) : PartyState()
}