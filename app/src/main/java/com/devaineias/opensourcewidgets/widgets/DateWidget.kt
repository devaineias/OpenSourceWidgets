package com.devaineias.opensourcewidgets.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.devaineias.opensourcewidgets.R
import java.text.SimpleDateFormat
import java.util.*

class DateWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, com.devaineias.opensourcewidgets.R.layout.date_widget)
            val now = Calendar.getInstance().time

            // Row 1: Day Number
            views.setTextViewText(com.devaineias.opensourcewidgets.R.id.text_day_number, SimpleDateFormat("d", Locale.getDefault()).format(now))

            // Row 2: Month Name
            views.setTextViewText(com.devaineias.opensourcewidgets.R.id.text_month, SimpleDateFormat("MMMM", Locale.getDefault()).format(now).uppercase())

            // Row 3: Day of week and Year
            views.setTextViewText(com.devaineias.opensourcewidgets.R.id.text_year_info, SimpleDateFormat("E yyyy", Locale.getDefault()).format(now).uppercase())

            // 1. Create the Intent to open the Calendar
            val calendarIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // 2. Wrap it in a PendingIntent (Required for Home Screen Widgets)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                calendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE is required for Android 12+
            )

            // 3. Attach the click listener to the root layout ID
            views.setOnClickPendingIntent(R.id.widget_date, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Schedule the next update for exactly midnight
        scheduleMidnightUpdate(context, appWidgetIds)
    }

    private fun scheduleMidnightUpdate(context: Context, appWidgetIds: IntArray) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, DateWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate time for exactly 12:00:00 AM tomorrow
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1) // 1 second past midnight to be safe
            set(Calendar.MILLISECOND, 0)
        }

        try {
            alarmManager.setExact(AlarmManager.RTC, midnight.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC, midnight.timeInMillis, pendingIntent)
        }
    }
}