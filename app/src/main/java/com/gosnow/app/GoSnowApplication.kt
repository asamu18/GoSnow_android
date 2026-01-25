package com.gosnow.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.gosnow.app.datasupabase.SupabaseClientProvider

// 实现 ImageLoaderFactory 接口
class GoSnowApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        SupabaseClientProvider.init(this)
        //SupabaseClientProvider.supabaseClient
    }

    // ✅ 配置全局 Coil 图片加载器
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 使用 25% 的内存做图片缓存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 使用 2% 的磁盘空间做缓存
                    .build()
            }
            // 强制启用网络缓存策略
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}