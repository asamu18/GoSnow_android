package com.gosnow.app.ui.snowcircle.data

import com.gosnow.app.ui.snowcircle.model.NotificationItem

interface NotificationsRepository {
    suspend fun getNotifications(currentUserId: String): List<NotificationItem>
    suspend fun markAllRead(currentUserId: String)
    suspend fun markRead(notificationId: Long)
}
