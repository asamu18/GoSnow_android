package com.gosnow.app.ui.record.storage

import com.gosnow.app.datasupabase.SupabaseClientProvider
import com.gosnow.app.ui.record.SkiSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// 对应 Supabase 数据库表结构
@Serializable
data class SkiSessionInsert(
    val id: String, // UUID
    @SerialName("user_id") val userId: String,
    @SerialName("start_at") val startAt: String, // ISO String
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
}