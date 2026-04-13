package com.devaineias.opensourcewidgets.utils

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.devaineias.opensourcewidgets.widgets.TimeWidget

class WidgetNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateWidgetCount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateWidgetCount()
    }

    private fun updateWidgetCount() {
        val activeNotifications = activeNotifications ?: return

        var emailCount = 0
        var generalCount = 0

        // Email apps
        val emailPackages = listOf("com.google.android.gm", "com.microsoft.office.outlook", "com.apple.android.mail", "eu.faircode.email")

        for (sbn in activeNotifications) {
            val notif = sbn.notification

            // 1. FILTER OUT THE JUNK
            // Ignore "Ongoing" notifications (like music players, VPNs, or system status)
            val isOngoing = (notif.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
            val isForeground = (notif.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0
            // Ignore the "Group Summary" (the header that says "2 new messages")
            val isSummary = (notif.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0

            if (isOngoing || isForeground || isSummary) continue

            // 2. IDENTIFY THE EMAIL
            // Check by Package Name OR by the System Category "Email"
            val isEmailCategory = notif.category == android.app.Notification.CATEGORY_EMAIL
            val isEmailPackage = sbn.packageName in emailPackages

            if (isEmailCategory || isEmailPackage) {
                emailCount++
            } else {
                generalCount++
            }
        }

        // 3. SAVE THE CLEAN DATA
        val prefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("notification_count", generalCount)
            putInt("email_count", emailCount)
            apply()
        }

        // Tell the widget to refresh
        val intent = Intent(this, TimeWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(application)
            .getAppWidgetIds(ComponentName(application, TimeWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }
}