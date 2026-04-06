package com.devaineias.opensourcewidgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.devaineias.opensourcewidgets.R

class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.search_widget)

            // Intent to open Google in the default browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))

            // PendingIntent for clicking the search bar
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.search_bar_container, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}