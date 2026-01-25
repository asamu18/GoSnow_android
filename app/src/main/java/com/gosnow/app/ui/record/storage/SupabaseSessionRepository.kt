package com.gosnow.app.ui.record.storage

import com.gosnow.app.ui.record.SkiSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SkiSessionInsert(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("duration_sec") val durationSec: Int,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("top_speed_kmh") val topSpeedKmh: Double,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double,
    @SerialName("vertical_drop_m") val verticalDropM: Int
)

class SupabaseSessionRepository(
    private val supabase: SupabaseClient
) {
    suspend fun uploadSession(session: SkiSession, userId: String) = withContext(Dispatchers.IO) {
        val payload = SkiSessionInsert(
            id = session.id,
            userId = userId,
            startAt = Instant.ofEpochMilli(session.startAtMillis).toString(),
            endAt = Instant.ofEpochMilli(session.endAtMillis).toString(),
            durationSec = session.durationSec,
            distanceKm = session.distanceKm,
            topSpeedKmh = session.topSpeedKmh,
            avgSpeedKmh = session.avgSpeedKmh,
            verticalDropM = session.verticalDropM
        )
        supabase.from("ski_sessions").insert(payload)
    }

    // ✅ 新增：从云端拉取该用户的所有滑行记录
    suspend fun fetchAllSessions(userId: String): List<SkiSession> = withContext(Dispatchers.IO) {
        try {
            val results = supabase.from("ski_sessions")
                .select {
                    filter { eq("user_id", userId) }
                    order("start_at", Order.DESCENDING)
                }
                .decodeList<SkiSessionInsert>()

            results.map { dto ->
                SkiSession(
                    id = dto.id,
                    startAtMillis = Instant.parse(dto.startAt).toEpochMilli(),
                    endAtMillis = Instant.parse(dto.endAt).toEpochMilli(),
                    durationSec = dto.durationSec,
                    distanceKm = dto.distanceKm,
                    topSpeedKmh = dto.topSpeedKmh,
                    avgSpeedKmh = dto.avgSpeedKmh,
                    verticalDropM = dto.verticalDropM
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}