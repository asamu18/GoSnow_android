package com.gosnow.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 头像压缩函数
 * 从 Uri 读取图片，并压缩到合适的分辨率 + 体积。
 *
 * 目标：
 * - 最长边不超过 maxSize（默认 512 px）
 * - 质量控制在 ~100～300KB 左右（视原图而定）
 */
suspend fun loadAndCompressImage(
    context: Context,
    uri: Uri,
    maxSize: Int = 512
): ByteArray = withContext(Dispatchers.IO) {

    // 1. 先只读取尺寸
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    }

    val (origW, origH) = options.outWidth to options.outHeight
    if (origW <= 0 || origH <= 0) {
        throw IllegalArgumentException("无法读取图片尺寸")
    }

    // 2. 计算采样率，先粗缩一轮
    var sampleSize = 1
    val maxOrigSide = max(origW, origH)
    if (maxOrigSide > maxSize) {
        sampleSize = maxOrigSide / maxSize
    }
    if (sampleSize < 1) sampleSize = 1

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565 // 省内存
    }

    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: throw IllegalArgumentException("无法解码图片")

    // 3. 精细缩放到最长边 maxSize
    val scale = min(
        maxSize.toFloat() / decoded.width.toFloat(),
        maxSize.toFloat() / decoded.height.toFloat()
    ).coerceAtMost(1f)

    val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)

    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
    } else {
        decoded
    }

    // 4. 压缩为 JPEG
    val baos = ByteArrayOutputStream()
    var quality = 85
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)

    // 简单控制一下体积：>300KB 就再压一轮（最多两次）
    var bytes = baos.toByteArray()
    while (bytes.size > 300_000 && quality > 50) {
        baos.reset()
        quality -= 15
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        bytes = baos.toByteArray()
    }

    // 如果 decode 和 scaled 不是同一个对象，记得回收
    if (scaled !== decoded) {
        decoded.recycle()
    }

    bytes
}


