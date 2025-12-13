package com.gosnow.app.ui.record.storage

import com.gosnow.app.ui.record.SkiSession

interface SessionStore {
    suspend fun saveSession(session: SkiSession)
    suspend fun loadSessions(): List<SkiSession>
}
