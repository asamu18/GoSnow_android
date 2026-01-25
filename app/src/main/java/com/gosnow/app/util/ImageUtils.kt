package com.gosnow.app.util

/**
 * 优化 Supabase 图片 URL
 */
fun getResizedImageUrl(originalUrl: String?, width: Int, quality: Int = 75): String? {
    if (originalUrl.isNullOrBlank()) return null

    // 🔴 修复：暂时直接返回原图 URL
    // 原因：MemFire 可能未开启 render API，导致请求压缩图返回 404/灰色。
    // 我们依赖 Coil 在客户端进行采样和缓存，这虽然耗一点流量，但最稳妥。
    return originalUrl

    /*
    // --- 下面是之前的服务端压缩逻辑，先注释掉 ---
    if (!originalUrl.contains("supabase") && !originalUrl.contains("memfiredb")) {
        return originalUrl
    }
    return try {
        val baseUrl = originalUrl.substringBefore("/storage/v1/")
        val path = originalUrl.substringAfter("/public/")
        "$baseUrl/storage/v1/render/image/public/$path?width=$width&quality=$quality&resize=cover"
    } catch (e: Exception) {
        originalUrl
    }
    */
}