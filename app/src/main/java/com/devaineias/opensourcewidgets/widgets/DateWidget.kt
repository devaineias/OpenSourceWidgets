package com.devaineias.opensourcewidgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.devaineias.opensourcewidgets.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.date_widget)
            val now = Calendar.getInstance().time

            // Row 1: Day Number (e.g., 5)
            views.setTextViewText(R.id.text_day_number, SimpleDateFormat("d", Locale.getDefault()).format(now))

            // Row 2: Month Name (e.g., April)
            views.setTextViewText(R.id.text_month, SimpleDateFormat("MMMM", Locale.getDefault()).format(now))

            // Row 3: Day of week and Year (e.g., Sun 2026)
            views.setTextViewText(
                R.id.text_year_info, SimpleDateFormat(
                    "E yyyy",
                    Locale.getDefault()
                ).format(now))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}