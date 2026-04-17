package com.devaineias.opensourcewidgets.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.net.URL
import org.json.JSONObject
import java.util.*
import androidx.core.net.toUri
import com.devaineias.opensourcewidgets.R

class TimeWidget : AppWidgetProvider() {

    data class WeatherResult(val temp: String, val iconRes: Int)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.time_widget)
            val calendar = Calendar.getInstance()

            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)

            // Update Time Words
            views.setTextViewText(R.id.text_hour_word, convertToWords(if (hour == 0) 12 else hour, false))
            views.setTextViewText(R.id.text_minute_word, convertToWords(minute, true))

            // 1. Setup Click Action
            val prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            val userCity = prefs.getString("city", "Athens") ?: "Athens"
            val targetPackage = prefs.getString("target_package", "DEFAULT")

            val intent: Intent? = if (targetPackage == "DEFAULT" || targetPackage == null) {
                // Option A: Open Google Weather/Search if no specific app is chosen
                context.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
            } else {
                // Option B: Open the specific app you selected in MainActivity
                context.packageManager.getLaunchIntentForPackage(targetPackage)
            }

            if (intent != null) {
                // Wrap the intent so the widget can fire it
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.weather_click_area, pendingIntent)
            }

            // 2. Weather Update Logic
            Thread {
                val unitType = prefs.getString("unit_type", "metric") ?: "metric"
                val unitLabel = prefs.getString("unit_label", "C") ?: "C"
                val lastUpdate = prefs.getLong("last_weather_update", 0)
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastUpdate >= 3600000) { // Hourly check
                    val result = fetchWeather(context, userCity, unitType, unitLabel)

                    if (result.temp != "N/A") {
                        prefs.edit().apply {
                            putString("last_temp_string", result.temp)
                            putInt("last_icon_res", result.iconRes)
                            putLong("last_weather_update", currentTime)
                            apply()
                        }
                        views.setTextViewText(R.id.count_weather, result.temp)
                        views.setImageViewResource(R.id.weather_icon, result.iconRes)
                    }
                } else {
                    // Use Cached Data
                    val cachedTemp = prefs.getString("last_temp_string", "--°$unitLabel")
                    val cachedIcon = prefs.getInt("last_icon_res", android.R.drawable.ic_menu_day)
                    views.setTextViewText(R.id.count_weather, cachedTemp)
                    views.setImageViewResource(R.id.weather_icon, cachedIcon)
                }
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }.start()

            // Update Notification Count
            val notifCount = prefs.getInt("notification_count", 0)
            val emailCount = prefs.getInt("email_count", 0)

            // Update layout
            views.setTextViewText(R.id.txt_notif_count, notifCount.toString())
            views.setTextViewText(R.id.txt_email_count, emailCount.toString())

            if (emailCount == 0) {
                views.setImageViewResource(
                    R.id.ic_email_count,
                    R.drawable.ic_mail_read
                )
            } else {
                views.setImageViewResource(R.id.ic_email_count, R.drawable.ic_mail_unread)
            }
            // Update the UI immediately
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Schedule the next exact update at the top of the next minute
        scheduleNextUpdate(context, appWidgetIds)
    }

    private fun scheduleNextUpdate(context: Context, appWidgetIds: IntArray) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, TimeWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate time exactly at the start of the next minute
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Set exact alarm to trigger the intent
        try {
            alarmManager.setExact(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback if the user revokes the exact alarm permission in settings
            alarmManager.set(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
        }
    }

    private fun convertToWords(num: Int, isMinute: Boolean): String {
        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty")

        // 1. Handle the special "Zero" cases immediately
        if (num == 0) {
            return if (isMinute) "O'Clock" else "Twelve"
        }

        // 2. Convert the raw number (1 to 59) into words
        val baseWord = if (num < 20) {
            units[num]
        } else {
            "${tens[num / 10]} ${units[num % 10]}".trim()
        }

        // 3. Apply the "Proper English" prefix for minutes
        return if (isMinute) {
            when (num) {
                in 1..9 -> "O' $baseWord"
                else -> "And $baseWord"
            }
        } else {
            // It's an hour, so just return the word
            baseWord
        }
    }

    private fun fetchWeather(context: Context, city: String, unitType: String, unitLabel: String): WeatherResult {
        val openWeatherKey = "API_KEY"
        return try {
            val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$city&units=$unitType&appid=$openWeatherKey")
            val response = Scanner(url.openStream()).useDelimiter("\\A").next()
            val json = JSONObject(response)

            val temp = json.getJSONObject("main").getInt("temp")
            val conditionId = json.getJSONArray("weather").getJSONObject(0).getInt("id")
            val iconCode = json.getJSONArray("weather").getJSONObject(0).getString("icon")

            val isNight = iconCode.endsWith("n")

            val tempSuffix = when {
                temp <= 7 -> "cold"
                temp >= 28 -> "hot"
                else -> "neutral"
            }

            val baseName = when (conditionId) {
                in 200..232 -> "lightning"
                in 300..531 -> "rain"
                in 600..622 -> "snow"
                800 -> if (isNight) "moon" else "sun"
                in 801..804 -> if (isNight) "cloud_moon" else "cloud_sun"
                else -> "sun" // Default fallback
            }

            val resourceName = "ic_${baseName}_$tempSuffix"

            val iconResId: Int = context.resources.getIdentifier(
                resourceName,
                "drawable",
                context.packageName
            )

            val finalIcon = if (iconResId != 0) iconResId else android.R.drawable.ic_menu_help

            WeatherResult("$temp°$unitLabel", finalIcon)
        } catch (e: Exception) {
            WeatherResult("N/A", android.R.drawable.ic_dialog_alert)
        }
    }
}