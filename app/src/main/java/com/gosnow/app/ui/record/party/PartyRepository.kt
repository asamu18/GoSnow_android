package com.gosnow.app.ui.record.party

import com.gosnow.app.datasupabase.SupabaseClientProvider
import com.gosnow.app.ui.snowcircle.data.supabase.PartyInsert
import com.gosnow.app.ui.snowcircle.data.supabase.PartyMemberInsert
import com.gosnow.app.ui.snowcircle.data.supabase.PartyRow
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PartyRepository {
    private val client get() = SupabaseClientProvider.supabaseClient

    /**
     * 1. 在 Party 表创建新队伍
     * 2. 将创建者作为 host 加入 party_member 表
     */
    suspend fun createPartyInDb(hostId: String, code: String): String = withContext(Dispatchers.IO) {
        // 插入 Party 表
        val party = client.from("party")
            .insert(PartyInsert(code = code, hostId = hostId)) { select() }
            .decodeSingle<PartyRow>()

        // 插入 Member 表 (自己是 Host)
        joinPartyDb(party.id, hostId, "host")

        return@withContext party.id
    }

    /**
     * 根据 4 位验证码查找 Party ID
     */
    suspend fun findPartyIdByCode(code: String): String? = withContext(Dispatchers.IO) {
        val result = client.from("party")
            .select {
                filter { eq("join_code", code) }
                limit(1)
            }
            .decodeList<PartyRow>()
        return@withContext result.firstOrNull()?.id
    }

    /**
     * 将用户写入 party_member 表
     */
    suspend fun joinPartyDb(partyId: String, userId: String, role: String = "member") = withContext(Dispatchers.IO) {
        // upsert 避免重复加入报错，依赖 onConflict 策略 (party_id, user_id)
        client.from("party_member")
            .upsert(
                PartyMemberInsert(partyId = partyId, userId = userId, role = role),
                onConflict = "party_id, user_id"
            )
    }
}