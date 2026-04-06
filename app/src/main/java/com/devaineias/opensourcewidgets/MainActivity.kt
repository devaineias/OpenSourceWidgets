package com.devaineias.opensourcewidgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import com.devaineias.opensourcewidgets.widgets.TimeWidget
import org.json.JSONArray
import java.net.URL
import java.util.Scanner

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Elements
        val cityInput = findViewById<AutoCompleteTextView>(R.id.city_input)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val unitGroup = findViewById<RadioGroup>(R.id.unit_group)
        val appSpinner = findViewById<Spinner>(R.id.app_spinner)

        // --- 1. SET UP APP PICKER ---
        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(launchIntent, 0)

        val appNames = mutableListOf<String>("System Default (Google Search)")
        val packageNames = mutableListOf<String>("DEFAULT")

        resolveInfos.forEach {
            appNames.add(it.loadLabel(pm).toString())
            packageNames.add(it.activityInfo.packageName)
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

    // Geocoding Helper Function
    fun fetchCitySuggestions(query: String): List<String> {
        val apiKey = "API_KEY"
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
}