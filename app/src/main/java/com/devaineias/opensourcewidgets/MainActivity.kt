package com.devaineias.opensourcewidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import com.devaineias.opensourcewidgets.widgets.TimeWidget
import org.json.JSONArray
import java.net.URL
import java.util.Scanner
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import android.view.View

class MainActivity : Activity() {

    data class WeatherAppInfo(
        val appName: String,
        val packageName: String,
        val icon: Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Elements
        val cityInput = findViewById<AutoCompleteTextView>(R.id.city_input)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val unitGroup = findViewById<RadioGroup>(R.id.unit_group)
        val appSpinner = findViewById<Spinner>(R.id.app_spinner)

        val btnPermission = findViewById<Button>(R.id.btn_permission)

        // 1. Check if we already have permission
        val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        // 2. Hide the button if they already granted it
        if (hasPermission) {
            btnPermission.visibility = View.GONE
        } else {
            btnPermission.visibility = View.VISIBLE
        }

        // 3. Set the click listener to open Android Settings
        btnPermission.setOnClickListener {
            // This intent opens the specific "Notification Access" screen
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // --- 1. SET UP APP PICKER ---
        val appNames = mutableListOf<String>("System Default (Google Search)")
        val packageNames = mutableListOf<String>("DEFAULT")

        // 1. Call helper function to get the filtered list
        val weatherApps = getInstalledWeatherApps(this)

        // 2. Loop through the filtered list
        weatherApps.forEach { appInfo ->
            appNames.add(appInfo.appName)
            packageNames.add(appInfo.packageName)
        }

        val appAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, appNames)
        appSpinner.adapter = appAdapter

        // --- 2. SET UP CITY SUGGESTIONS (API) ---
        cityInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.length >= 3) {
                    Thread {
                        val results = fetchCitySuggestions(query)
                        runOnUiThread {
                            val adapter = ArrayAdapter(this@MainActivity,
                                android.R.layout.simple_dropdown_item_1line, results)
                            cityInput.setAdapter(adapter)
                            // Don't call notifyDataSetChanged here, just showDropDown
                            cityInput.showDropDown()
                        }
                    }.start()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // --- 3. SAVE LOGIC ---
        btnSave.setOnClickListener {
            val selectedCity = cityInput.text.toString().trim()
            val selectedAppPackage = packageNames[appSpinner.selectedItemPosition]

            val unit = if (unitGroup.checkedRadioButtonId == R.id.radio_celsius) "metric" else "imperial"
            val unitLabel = if (unit == "metric") "C" else "F"

            if (selectedCity.isNotEmpty()) {
                val prefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("city", selectedCity)
                    putString("unit_type", unit)
                    putString("unit_label", unitLabel)
                    putString("target_package", selectedAppPackage)
                    putLong("last_weather_update", 0) // Force refresh
                    apply()
                }

                Toast.makeText(this, "Settings Saved for $selectedCity", Toast.LENGTH_SHORT).show()

                // Notify TimeWidget to update immediately
                val intent = Intent(this, TimeWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(application)
                    .getAppWidgetIds(ComponentName(application, TimeWidget::class.java))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                sendBroadcast(intent)
            } else {
                Toast.makeText(this, "Please select a city", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val btnPermission = findViewById<Button>(R.id.btn_permission)
        val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        if (hasPermission) {
            btnPermission.visibility = View.GONE
        } else {
            btnPermission.visibility = View.VISIBLE
        }
    }

    // Geocoding Helper Function
    fun fetchCitySuggestions(query: String): List<String> {
        val apiKey = "abc"
        val limit = 5
        val suggestions = mutableListOf<String>()

        return try {
            // The Geocoding API: http://api.openweathermap.org/geo/1.0/direct
            val url = URL("https://api.openweathermap.org/geo/1.0/direct?q=$query&limit=$limit&appid=$apiKey")
            val response = Scanner(url.openStream()).useDelimiter("\\A").next()
            val jsonArray = JSONArray(response)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val country = obj.getString("country")
                val state = if (obj.has("state")) obj.getString("state") else ""

                // Format: "City, State, Country" (or "City, Country" if no state)
                val fullString = if (state.isNotEmpty()) "$name, $state, $country" else "$name, $country"
                suggestions.add(fullString)
            }
            suggestions
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getInstalledWeatherApps(context: Context): List<WeatherAppInfo> {
        val packageManager = context.packageManager

        // Intent to find all apps that show up in the launcher
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // Query all installed launcher activities
        val availableActivities: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        val weatherApps = mutableListOf<WeatherAppInfo>()

        for (resolveInfo in availableActivities) {
            val appName = resolveInfo.loadLabel(packageManager).toString()
            val packageName = resolveInfo.activityInfo.packageName

            // Filter: Check if the app's display name OR package name contains "weather"
            if (appName.contains("weather", ignoreCase = true) ||
                packageName.contains("weather", ignoreCase = true)) {

                val icon = resolveInfo.loadIcon(packageManager)
                weatherApps.add(WeatherAppInfo(appName, packageName, icon))
            }
        }

        // Sort the list alphabetically by app name for a better user experience
        return weatherApps.sortedBy { it.appName }
    }
}