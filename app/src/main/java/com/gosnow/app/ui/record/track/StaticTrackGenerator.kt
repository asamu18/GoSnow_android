package com.gosnow.app.ui.record.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object StaticTrackGenerator {

    suspend fun generateAndSave(
        context: Context,
        sessionId: String,
        segments: List<TrackSegment>
    ): File? = withContext(Dispatchers.Default) {
        if (segments.isEmpty()) return@withContext null

        val width = 900
        val height = 900
        val padding = 70f

        // 1. 找边界
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0

        segments.forEach { seg ->
            seg.points.forEach { p ->
                minLat = min(minLat, p.latitude())
                maxLat = max(maxLat, p.latitude())
                minLon = min(minLon, p.longitude())
                maxLon = max(maxLon, p.longitude())
            }
        }

        // 避免单点导致除0
        if (minLat == maxLat || minLon == maxLon) return@withContext null

        // 2. 准备画布
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 背景透明或者深色，看需求
        // canvas.drawColor(Color.TRANSPARENT)

        // 3. 画笔设置
        val casingPaint = Paint().apply {
            color = Color.BLACK
            alpha = 140 // ~0.55
            strokeWidth = 12f // 稍微粗一点因为 bitmap 比较大
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val greenPaint = Paint().apply {
            color = Color.parseColor("#00C853") // Green
            alpha = 242 // ~0.95
            strokeWidth = 7f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val orangePaint = Paint().apply {
            color = Color.parseColor("#FF9500") // Orange
            alpha = 242
            strokeWidth = 7f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        // 4. 坐标映射函数
        val usableW = width - padding * 2
        val usableH = height - padding * 2
        val latSpan = maxLat - minLat
        val lonSpan = maxLon - minLon

        // 保持比例
        val scaleX = usableW / lonSpan
        val scaleY = usableH / latSpan
        val scale = min(scaleX, scaleY)

        // 居中偏移
        val offsetX = (width - lonSpan * scale) / 2
        val offsetY = (height - latSpan * scale) / 2

        fun mapX(lon: Double): Float = ((lon - minLon) * scale + offsetX).toFloat()
        fun mapY(lat: Double): Float = (height - ((lat - minLat) * scale + offsetY)).toFloat() // Y轴反转

        // 5. 绘制 (两遍：先描边，再主线)

        // Pass 1: Casing
        segments.forEach { seg ->
            if (seg.points.size < 2) return@forEach
            val path = Path()
            path.moveTo(mapX(seg.points[0].longitude()), mapY(seg.points[0].latitude()))
            for (i in 1 until seg.points.size) {
                path.lineTo(mapX(seg.points[i].longitude()), mapY(seg.points[i].latitude()))
            }
            canvas.drawPath(path, casingPaint)
        }

        // Pass 2: Main Line
        segments.forEach { seg ->
            if (seg.points.size < 2) return@forEach
            val path = Path()
            path.moveTo(mapX(seg.points[0].longitude()), mapY(seg.points[0].latitude()))
            for (i in 1 until seg.points.size) {
                path.lineTo(mapX(seg.points[i].longitude()), mapY(seg.points[i].latitude()))
            }
            val paint = if (seg.isFast) orangePaint else greenPaint
            canvas.drawPath(path, paint)
        }

        // 6. 保存到文件
        val file = File(context.filesDir, "track_$sessionId.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return@withContext file
    }
}