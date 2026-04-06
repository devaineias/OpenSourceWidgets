package com.devaineias.opensourcewidgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.devaineias.opensourcewidgets.R
import java.util.Calendar
import java.net.URL
import org.json.JSONObject
import java.util.Scanner

class TimeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.time_widget)

            // Update Time Words
            val calendar = Calendar.getInstance()
            views.setTextViewText(R.id.text_hour_word, convertToWords(calendar.get(Calendar.HOUR)))
            views.setTextViewText(R.id.text_minute_word, convertToWords(calendar.get(Calendar.MINUTE)))

            // Fetch Weather in Background
            Thread {
                val currentTemp = getTemperature("City", "C") // Define city and unit here for now
                views.setTextViewText(R.id.count_weather, currentTemp)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }.start()

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun convertToWords(num: Int): String {
        val units = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty")

        return when {
            num == 0 -> "O'Clock"
            num < 20 -> units[num]
            else -> "${tens[num / 10]} ${units[num % 10]}".trim()
        }
    }

    fun getTemperature(city: String, unit: String): String {
        val openWeatherKey = "openweathermap key"
        // OpenWeather uses 'metric' for Celsius and 'imperial' for Fahrenheit
        val owUnit = if (unit.lowercase() == "c") "metric" else "imperial"

        return try {
            val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$city&units=$owUnit&appid=$openWeatherKey")
            val response = Scanner(url.openStream()).useDelimiter("\\A").next()
            val json = JSONObject(response)
            val temp = json.getJSONObject("main").getInt("temp")
            "$temp°$unit"
        } catch (e: Exception) {
            "N/A" // If API fails or city is wrong
        }
    }
}