package com.gosnow.app.ui.record

import android.Manifest
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
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

@Composable
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
fun RecordScreen(
    viewModel: RecordingViewModel,
    onBack: () -> Unit,
    hasLocationPermission: Boolean,
    hasFineLocation: Boolean,
    hasNotificationPermission: Boolean,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val partyState by viewModel.partyState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }

    // 监听小队反馈事件
    LaunchedEffect(viewModel.partyManager) {
        viewModel.partyManager.statusEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 地图
        if (!hasLocationPermission) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F2F7)), contentAlignment = Alignment.Center) {
                Text("需要定位权限以显示地图", color = Color.Gray)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapboxMap.loadStyle("mapbox://styles/gosnow/cmikjh06p00ys01s68fmy9nor") { style ->
                            location.updateSettings { enabled = true; pulsingEnabled = true }
                            viewport.transitionTo(viewport.makeFollowPuckViewportState(), viewport.makeImmediateViewportTransition())
                            setupTrackLayers(style)
                        }
                    }
                },
                update = { mapView ->
                    if (viewModel.isRecording) {
                        val (green, orange) = viewModel.trackController.getGeoJsonData()
                        mapView.mapboxMap.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>("source-green")?.featureCollection(green)
                            style.getSourceAs<GeoJsonSource>("source-orange")?.featureCollection(orange)
                        }
                    }
                }
            )
        }

        // --- 小队面板 ---
        Column(
            modifier = Modifier.padding(top = 80.dp, start = 16.dp).align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (val s = partyState) {
                is PartyState.Idle -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.partyManager.createParty() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Groups, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("创建小队", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                joinCodeInput = ""
                                showJoinDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("加入小队", fontSize = 12.sp)
                        }
                    }
                }
                is PartyState.Joined -> {
                    Surface(color = Color(0xCC000000), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("小队邀请码", color = Color.LightGray, fontSize = 10.sp)
                                    Text(s.code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(24.dp))
                                IconButton(onClick = { viewModel.partyManager.leaveParty() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Logout, "退出", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (s.members.isNotEmpty()) {
                                Divider(Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                                s.members.forEach { member ->
                                    Text("• ${member.userName ?: "加载中..."}", color = Color(0xFF00FF88), fontSize = 12.sp)
                                }
                            } else {
                                Text("等待队友...", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color(0x66000000), RoundedCornerShape(999.dp))
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }

        // 状态徽章 (右上角)
        MotionModeBadge(viewModel.isRecording, viewModel.motionMode, hasLocationPermission, hasFineLocation, Modifier.padding(top = 16.dp, end = 16.dp).align(Alignment.TopEnd))

        // 底部数据面板
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(16.dp), color = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
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

        // 加入小队弹窗
        if (showJoinDialog) {
            AlertDialog(
                onDismissRequest = { showJoinDialog = false },
                title = { Text("加入小队") },
                text = {
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) joinCodeInput = it },
                        label = { Text("输入4位邀请码") },
                        placeholder = { Text("例如: 1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (joinCodeInput.length == 4) {
                                viewModel.partyManager.joinParty(joinCodeInput)
                                showJoinDialog = false
                            } else {
                                Toast.makeText(context, "请输入4位数字邀请码", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text("确认加入") }
                },
                dismissButton = { TextButton(onClick = { showJoinDialog = false }) { Text("取消") } }
            )
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