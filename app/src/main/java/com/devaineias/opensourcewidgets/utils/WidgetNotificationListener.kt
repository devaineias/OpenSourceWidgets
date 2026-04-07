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
        val activeNotifications = activeNotifications

        // Count total notifications
        val totalCount = activeNotifications.size

        // Count "Email" specifically by checking package names
        val emailPackages = listOf("com.google.android.gm", "com.microsoft.office.outlook", "com.apple.android.mail")
        val emailCount = activeNotifications.count { it.packageName in emailPackages }

        // Save these to SharedPreferences so the widget can read them
        val prefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("notification_count", totalCount)
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