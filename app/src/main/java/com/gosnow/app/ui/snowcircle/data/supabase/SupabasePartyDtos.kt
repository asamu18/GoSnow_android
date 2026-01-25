package com.gosnow.app.ui.snowcircle.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 对应数据库表 public.party
 * 假设表结构为: id(uuid), code(text), host_id(uuid)
 */
@Serializable
data class PartyRow(
    val id: String,

    @SerialName("join_code") val code: String,
    @SerialName("host_id") val hostId: String
)

/**
 * 用于向 public.party 插入数据
 */
@Serializable
data class PartyInsert(

    @SerialName("join_code") val code: String,
    @SerialName("host_id") val hostId: String
)

/**
 * 对应数据库表 public.party_member (根据你的截图定义)
 * 字段: party_id, user_id, role
 */
@Serializable
data class PartyMemberInsert(
    @SerialName("party_id") val partyId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member"
)