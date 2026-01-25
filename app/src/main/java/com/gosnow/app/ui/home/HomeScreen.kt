package com.gosnow.app.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gosnow.app.datasupabase.CurrentUserProfile
import com.gosnow.app.datasupabase.CurrentUserStore
import com.gosnow.app.datasupabase.SupabaseClientProvider
import com.gosnow.app.ui.record.storage.FileSessionStore
import com.gosnow.app.ui.record.storage.SupabaseSessionRepository
import com.gosnow.app.ui.record.SkiSession
import com.gosnow.app.ui.theme.GosnowTheme
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * UI state for the Home screen.
 */
data class HomeUiState(
    val todayDistanceKm: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val totalDurationHours: Double = 0.0,
    val daysOnSnow: Int = 0,
    val deltaVsYesterdayKm: Double? = null
)

class HomeViewModel(
    private val store: FileSessionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // 1. 先刷一次本地
        refresh()
        // 2. 触发云同步
        android.util.Log.d("SyncTest", "HomeViewModel Init: 启动同步程序")
        syncFromCloud()
    }

    fun refresh() {
        viewModelScope.launch {
            val sessions = store.loadSessions()

            val zone = ZoneId.systemDefault()
            fun sessionDate(millis: Long): LocalDate =
                Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

            val today = LocalDate.now(zone)
            val yesterday = today.minusDays(1)

            val todayDistance = sessions
                .filter { sessionDate(it.startAtMillis) == today }
                .sumOf { it.distanceKm }

            val yesterdayDistance = sessions
                .filter { sessionDate(it.startAtMillis) == yesterday }
                .sumOf { it.distanceKm }

            val totalDistance = sessions.sumOf { it.distanceKm }
            val totalDurationHours = sessions.sumOf { it.durationSec }.toDouble() / 3600.0

            val daysOnSnow = sessions
                .map { sessionDate(it.startAtMillis) }
                .distinct()
                .size

            val delta = if (sessions.any { sessionDate(it.startAtMillis) == yesterday }) {
                todayDistance - yesterdayDistance
            } else {
                null
            }

            _uiState.update {
                it.copy(
                    todayDistanceKm = todayDistance,
                    totalDistanceKm = totalDistance,
                    totalDurationHours = totalDurationHours,
                    daysOnSnow = daysOnSnow,
                    deltaVsYesterdayKm = delta
                )
            }
        }
    }

    // ✅ 新增：云同步逻辑
    private fun syncFromCloud() {
        viewModelScope.launch {
            val client = SupabaseClientProvider.supabaseClient

            // 等待 Auth 初始化，避免拿到 null
            var user = client.auth.currentUserOrNull()
            if (user == null) {
                kotlinx.coroutines.delay(1000) // 给 SDK 一秒钟加载 Session
                user = client.auth.currentUserOrNull()
            }

            if (user == null) {
                android.util.Log.e("SyncTest", "同步终止：当前没有登录用户")
                return@launch
            }

            val localSessions = store.loadSessions()
            if (localSessions.isEmpty()) {
                android.util.Log.d("SyncTest", "检测到本地数据为空，开始从云端拉取...")
                val repo = SupabaseSessionRepository(client)
                val remoteSessions = repo.fetchAllSessions(user.id)

                android.util.Log.d("SyncTest", "云端拉取成功，条数: ${remoteSessions.size}")

                if (remoteSessions.isNotEmpty()) {
                    remoteSessions.forEach { store.saveSession(it) }
                    refresh() // 重新刷新本地计算逻辑
                }
            } else {
                android.util.Log.d("SyncTest", "本地已有 ${localSessions.size} 条数据，跳过自动同步")
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val store = FileSessionStore(context.applicationContext)
                    return HomeViewModel(store) as T
                }
            }
    }
}

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Record : BottomNavItem("home", "记录", Icons.Filled.RadioButtonChecked)
    data object Community : BottomNavItem("feed", "雪圈", Icons.Filled.Groups)
    data object Discover : BottomNavItem("discover", "发现", Icons.Filled.Explore)
}

@Composable
fun HomeScreen(
    onStartRecording: () -> Unit,
    onFeatureClick: (String) -> Unit,
    onAvatarClick: () -> Unit,
    onBottomNavSelected: (BottomNavItem) -> Unit,
    currentRoute: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(context)
    )

    val uiState by viewModel.uiState.collectAsState()
    val currentProfile by CurrentUserStore.profile.collectAsState()

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            HeaderSection(
                profile = currentProfile,
                onAvatarClick = onAvatarClick
            )

            Spacer(modifier = Modifier.height(20.dp))
            TodayHeroCard(uiState)
            Spacer(modifier = Modifier.height(24.dp))
            LifetimeStatsSection(uiState)
            Spacer(modifier = Modifier.height(28.dp))
            FeaturedSection(onFeatureClick = onFeatureClick)
            Spacer(modifier = Modifier.height(32.dp))
            PrimaryActionButton(onStartRecording = onStartRecording)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/* ------------------------ Header ------------------------ */
@Composable
private fun HeaderSection(
    profile: CurrentUserProfile?,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "25–26 雪季",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            ),
            color = Color(0xFF111111)
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF111111))
                .clickable { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            when {
                !profile?.avatarUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = profile!!.avatarUrl,
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !profile?.userName.isNullOrBlank() -> {
                    val initial = profile!!.userName.first().uppercaseChar().toString()
                    Text(
                        text = initial,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        text = "G",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/* ------------------------ Today Hero Card ------------------------ */

@Composable
private fun TodayHeroCard(uiState: HomeUiState) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日滑行",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF111111)
                )

                uiState.deltaVsYesterdayKm?.let { delta ->
                    if (delta != 0.0) {
                        TodayDeltaPill(deltaKm = delta)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = String.format("%.1f", uiState.todayDistanceKm),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 52.sp
                    ),
                    color = Color(0xFF111111)
                )
                Text(
                    text = "km",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF3A3A3C)
                )
            }
        }
    }
}

@Composable
private fun TodayDeltaPill(deltaKm: Double) {
    val isPositive = deltaKm > 0
    val bgColor = if (isPositive) Color(0xFFE6F9EA) else Color(0xFFFFF2E8)
    val textColor = if (isPositive) Color(0xFF1A7F37) else Color(0xFFC23B00)
    val label = if (isPositive) "比昨天多" else "比昨天少"
    val valueText = String.format("%.1f km", abs(deltaKm))

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isPositive) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
            Text(
                text = "$label $valueText",
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}

/* ------------------------ Lifetime Stats ------------------------ */

@Composable
private fun LifetimeStatsSection(uiState: HomeUiState) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "生涯数据",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF111111)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DistanceStatCard(
                title = "总里程",
                value = String.format("%.1f", uiState.totalDistanceKm),
                icon = Icons.Filled.DownhillSkiing,
                iconTint = Color(0xFF0A84FF),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "总时长",
                    value = String.format("%.1f 小时", uiState.totalDurationHours),
                    icon = Icons.Filled.AccessTime,
                    iconTint = Color(0xFFFF9F0A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                StatCard(
                    title = "在雪天数",
                    value = "${uiState.daysOnSnow} 天",
                    icon = Icons.Filled.AcUnit,
                    iconTint = Color(0xFF5E5CE6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DistanceStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6E6E73)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp
                ),
                color = Color(0xFF111111),
                modifier = Modifier.align(Alignment.CenterStart)
            )

            Text(
                text = "km",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6E6E73),
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6E6E73)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF111111)
                )
            }
        }
    }
}

/* ------------------------ Featured section ------------------------ */

@Composable
private fun FeaturedSection(onFeatureClick: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        Text(
            text = "精选",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF111111),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(featureItems) { item ->
                FeatureTile(
                    item = item,
                    onClick = { onFeatureClick(item.title) }
                )
            }
        }
    }
}

private val featureItems = listOf(
    FeatureTileData(
        title = "滑行数据",
        subtitle = "周 / 月 / 雪季趋势图表",
        icon = Icons.Filled.BarChart
    ),
    FeatureTileData(
        title = "更多功能",
        subtitle = "更多功能赶来中",
        icon = Icons.Filled.Explore
    )
)

@Composable
private fun FeatureTile(item: FeatureTileData, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White
        ),
        modifier = Modifier.width(220.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF111111),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF111111)
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6E6E73),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class FeatureTileData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

/* ------------------------ Primary CTA button ------------------------ */

@Composable
private fun PrimaryActionButton(onStartRecording: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onStartRecording,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111111),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "开始记录",
                modifier = Modifier.padding(vertical = 6.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/* ------------------------ Preview ------------------------ */

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    GosnowTheme {
        HomeScreen(
            onStartRecording = {},
            onFeatureClick = { _ -> },
            onAvatarClick = {},
            onBottomNavSelected = {},
            currentRoute = "home"
        )
    }
}