package com.gosnow.app.ui.record

import androidx.compose.material.icons.filled.ContentCopy
import android.Manifest
import androidx.compose.material.icons.filled.PersonAdd
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.outlined.Landscape
// 2. 导入 BottomSheet 组件
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import com.gosnow.app.ui.record.classifier.MotionMode
import com.gosnow.app.ui.snowcircle.model.PartyState
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
// ✅ Mapbox v11 ViewAnnotation 核心导入
import com.mapbox.geojson.Point
// ✅ 引入 DSL 构建函数
// 建议删除旧的 viewannotation 相关引用，只保留下面这些：
import com.mapbox.maps.viewannotation.viewAnnotationOptions // DSL构建函数
import com.mapbox.maps.viewannotation.geometry             // DSL属性
import coil.transform.CircleCropTransformation // 图片裁剪
import android.widget.ImageView
import com.gosnow.app.R
import coil.load
import androidx.compose.foundation.shape.CircleShape
import android.view.ViewGroup
import android.widget.FrameLayout
// ✅ Mapbox 核心 ViewAnnotation 导入
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.maps.ViewAnnotationAnchor// 👈 确保这个类能被索引到
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.annotationAnchor // 👈 v11 的 DSL 扩展名是这个
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs


// 以及其他原有导入

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecordRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: RecordingViewModel = viewModel()

    DisposableEffect(Unit) {
        vm.bindService()
        onDispose { vm.unbindService() }
    }

    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasFineLocation by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(true) }

    fun checkPermissions() {
        hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = hasFineLocation || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { checkPermissions() }

    LaunchedEffect(Unit) {
        checkPermissions()
        val toRequest = mutableListOf<String>()
        if (!hasLocationPermission) {
            toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            toRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission) toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
    }

    RecordScreen(vm, onBack, hasLocationPermission, hasFineLocation, hasNotificationPermission) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecordScreen(
    viewModel: RecordingViewModel,
    onBack: () -> Unit,
    hasLocationPermission: Boolean,
    hasFineLocation: Boolean,
    hasNotificationPermission: Boolean,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    var showPartySheet by remember { mutableStateOf(false) }
    val partyState by viewModel.partyState.collectAsState()

    // 调试日志：直接在 UI 上打印当前队友数量，确认数据层是否工作
    val debugMemberCount = (partyState as? PartyState.Joined)?.members?.size ?: 0
    val debugMembers = (partyState as? PartyState.Joined)?.members ?: emptyList()

    // 缓存 ViewAnnotation 对应的 View，避免重复创建
    val markerViews = remember { mutableMapOf<String, android.view.View>() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasLocationPermission) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F2F7)), contentAlignment = Alignment.Center) {
                Text("需要定位权限以显示地图", color = Color.Gray)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        // 你的 Style URL
                        mapboxMap.loadStyle("mapbox://styles/gosnow/cmikjh06p00ys01s68fmy9nor") { style ->
                            location.updateSettings { enabled = true; pulsingEnabled = true }
                            viewport.transitionTo(viewport.makeFollowPuckViewportState(), viewport.makeImmediateViewportTransition())
                            setupTrackLayers(style)
                        }
                    }
                },
                update = { mapView ->
                    // 强制刷新标记
                    val tick = viewModel.trackUpdateTick
                    // =================================================
                    // ✅ 1. 修复滑行轨迹 (新增代码)
                    // =================================================
                    val style = mapView.mapboxMap.getStyle()
                    if (style != null) {
                        val (greenData, orangeData) = viewModel.trackController.getGeoJsonData()

                        // 更新绿色轨迹源 (慢速/普通)
                        style.getSourceAs<GeoJsonSource>("source-green")?.featureCollection(greenData)

                        // 更新橙色轨迹源 (快速)
                        style.getSourceAs<GeoJsonSource>("source-orange")?.featureCollection(orangeData)
                    }
                    // =================================================
                    val vaManager = mapView.viewAnnotationManager
                    val currentState = partyState

                    if (currentState is PartyState.Joined) {
                        val currentMembers = currentState.members
                        val currentIds = currentMembers.map { it.userId }.toSet()

                        // 1. 删除已离开的队友
                        val iterator = markerViews.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (!currentIds.contains(entry.key)) {
                                vaManager.removeViewAnnotation(entry.value)
                                iterator.remove()
                            }
                        }

                        // 2. 添加或更新队友
                        currentMembers.forEach { member ->
                            // 过滤非法坐标 (0,0 在非洲，显示出来也没用)
                            if (member.lat != 0.0 && member.lon != 0.0) {
                                val existingView = markerViews[member.userId]
                                val point = Point.fromLngLat(member.lon, member.lat)

                                if (existingView == null) {
                                    // --- 创建新 View ---
                                    // 使用 FrameLayout 包裹圆形图片，更容易控制大小
                                    val container = FrameLayout(context).apply {
                                        layoutParams = ViewGroup.LayoutParams(80, 80) // 确保有大小！

                                        clipToOutline = true
                                    }

                                    val imageView = ImageView(context).apply {
                                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                        load(member.avatarUrl) {
                                            crossfade(true)
                                            placeholder(R.drawable.ic_launcher_foreground)
                                            error(R.drawable.ic_launcher_foreground) // 错误时显示
                                            transformations(CircleCropTransformation())
                                        }
                                    }
                                    container.addView(imageView)

                                    // 添加到地图
                                    val options = viewAnnotationOptions {
                                        geometry(point)
                                        allowOverlap(true) // 允许重叠，防止被自己的位置遮挡
                                        // ✅ v11 使用 annotationAnchor
                                        annotationAnchor {
                                            anchor(ViewAnnotationAnchor.CENTER)
                                        }
                                        // 或者简写版（取决于具体扩展包版本）：
                                        // annotationAnchor(ViewAnnotationAnchor.CENTER)
                                    }
                                    vaManager.addViewAnnotation(container, options)
                                    markerViews[member.userId] = container
                                } else {
                                    // --- 更新位置 ---
                                    val options = viewAnnotationOptions {
                                        geometry(point)
                                    }
                                    vaManager.updateViewAnnotation(existingView, options)
                                }
                            }
                        }
                    } else {
                        if (markerViews.isNotEmpty()) {
                            vaManager.removeAllViewAnnotations()
                            markerViews.clear()
                        }
                    }
                }
            )
        }



        // --- 右侧按钮栏 ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = { showPartySheet = true },
                containerColor = if (partyState is PartyState.Joined) Color(0xFFC6FF3F) else Color(0xCC000000),
                contentColor = if (partyState is PartyState.Joined) Color.Black else Color.White,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (partyState is PartyState.Joined) Icons.Default.Groups else Icons.Default.PersonAdd,
                    contentDescription = "小队",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // --- 下方：返回按钮、数据面板等 (保持原样) ---
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color(0x66000000), RoundedCornerShape(999.dp))
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }

        MotionModeBadge(viewModel.isRecording, viewModel.motionMode, hasLocationPermission, hasFineLocation, Modifier.padding(top = 16.dp, end = 16.dp).align(Alignment.TopEnd))

        // 底部数据面板 (简写，保持原样)
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp), color = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            // ... (复制你之前的 StatCard 内容，这里省略以节省空间，只展示逻辑) ...
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AccessTime, null, tint = Color(0xFF8E8E93))
                    Spacer(Modifier.height(8.dp))
                    Text(viewModel.durationText, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), "滑行里程", String.format("%.1f km", viewModel.distanceKm), Icons.Filled.TrendingUp)
                    StatCard(Modifier.weight(1f), "最高速度", String.format("%.0f km/h", viewModel.maxSpeedKmh), Icons.Filled.Speed)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), "累计落差", "${viewModel.verticalDropM} m", Icons.Outlined.Landscape)
                    val cardColor = if (viewModel.isRecording) Color(0xFFC6FF3F) else Color(0xFF1C1C1E)
                    val contentColor = if (viewModel.isRecording) Color.Black else Color.White
                    Column(modifier = Modifier.weight(1f).height(88.dp).background(cardColor, RoundedCornerShape(20.dp)).clickable { if (!hasLocationPermission || !hasFineLocation) requestPermissions() else viewModel.onToggleRecording() }.padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (viewModel.isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, tint = contentColor)
                            Text(if (viewModel.isRecording) "结束" else "开始", color = contentColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Bottom Sheet ---
        val sheetState = rememberModalBottomSheetState()
        if (showPartySheet) {
            ModalBottomSheet(
                onDismissRequest = { showPartySheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1C1C1E)
            ) {
                PartySheetContent(
                    viewModel = viewModel,
                    onDismiss = { showPartySheet = false }
                )
            }
        }
    }
}

// 辅助组件：图层设置
private fun setupTrackLayers(style: Style) {
    style.addSource(geoJsonSource("source-green"))
    style.addLayer(lineLayer("layer-green-casing", "source-green") { lineColor("black"); lineWidth(6.0); lineOpacity(0.55); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND) })
    style.addLayer(lineLayer("layer-green-main", "source-green") { lineColor("#00C853"); lineWidth(3.5); lineOpacity(0.95); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND) })
    style.addSource(geoJsonSource("source-orange"))
    style.addLayer(lineLayer("layer-orange-casing", "source-orange") { lineColor("black"); lineWidth(6.0); lineOpacity(0.55); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND) })
    style.addLayer(lineLayer("layer-orange-main", "source-orange") { lineColor("#FF9500"); lineWidth(3.5); lineOpacity(0.95); lineCap(LineCap.ROUND); lineJoin(LineJoin.ROUND) })
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier.height(88.dp).background(Color(0xFFF5F5F7), RoundedCornerShape(20.dp)).padding(16.dp, 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = Color(0xFF6E6E73), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        Text(value, color = Color.Black, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MotionModeBadge(isRecording: Boolean, mode: MotionMode, hasPerm: Boolean, hasFine: Boolean, modifier: Modifier) {
    val (text, bg) = when {
        !hasPerm -> "无权限" to Color(0xFFFF3B30)
        !hasFine -> "弱定位" to Color(0xFFFF9500)
        !isRecording -> "准备" to Color(0xFF8E8E93)
        mode == MotionMode.LIFT -> "缆车" to Color(0xFF5856D6)
        mode == MotionMode.IDLE -> "静止" to Color(0xFFFF9500)
        else -> "滑行" to Color(0xFF34C759)
    }
    Surface(modifier, color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp, 6.dp))
    }
}

@Composable
fun PartySheetContent(
    viewModel: RecordingViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val partyState by viewModel.partyState.collectAsState()
    var joinCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val s = partyState) {
            is PartyState.Idle -> {
                Text("尚未加入小队", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))

                // 创建按钮
                Button(
                    onClick = { viewModel.partyManager.createParty() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("创建新小队", color = Color.Black)
                }

                Spacer(Modifier.height(16.dp))
                Text("或", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))

                // 加入输入框
                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) joinCodeInput = it },
                    label = { Text("输入4位邀请码", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { if (joinCodeInput.length == 4) viewModel.partyManager.joinParty(joinCodeInput) },
                    enabled = joinCodeInput.length == 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("加入小队")
                }
            }
            is PartyState.Joined -> {
                // 已加入状态：显示邀请码和成员
                Text("小队邀请码", color = Color.Gray, fontSize = 12.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(s.code))
                        Toast.makeText(context, "邀请码已复制", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(s.code, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ContentCopy, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.height(24.dp))
                Divider(color = Color.DarkGray)
                Spacer(Modifier.height(16.dp))

                Text("在线成员 (${s.members.size + 1})", modifier = Modifier.align(Alignment.Start), color = Color.Gray, fontSize = 12.sp)

                // 成员列表
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 自己
                    MemberRow(name = "我 (自己)", avatar = null, isMe = true)
                    // 队友
                    s.members.forEach { member ->
                        MemberRow(name = member.userName ?: "雪友", avatar = member.avatarUrl, isMe = false)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // 退出/结束按钮
                TextButton(
                    onClick = { viewModel.partyManager.leaveParty() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (s.isHost) "结束并离开小队" else "退出当前小队", color = Color.Red)
                }
            }
        }
    }
}
@Composable
fun MemberRow(
    name: String,
    avatar: String?,
    isMe: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // 头像预览/点位标记颜色
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isMe) Color(0xFFC6FF3F) else Color(0xFF00C853)), // 自己用黄绿，队友用纯绿
            contentAlignment = Alignment.Center
        ) {
            if (!avatar.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                // 无头像显示名字首字母
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
