package com.gosnow.app

// com/gosnow/app/GoSnowApplication.kt


import android.app.Application
import com.gosnow.app.datasupabase.SupabaseManager

class GoSnowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseManager.initialize(this)
    }
}
