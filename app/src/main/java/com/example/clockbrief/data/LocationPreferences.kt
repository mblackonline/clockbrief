package com.example.clockbrief.data

import android.content.Context
import android.content.SharedPreferences

data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

class LocationPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("clockbrief_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
    }

    fun getLocation(): SavedLocation? {
        val name = prefs.getString(KEY_CITY_NAME, null) ?: return null
        val lat = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        return SavedLocation(name = name, latitude = lat, longitude = lon)
    }

    fun saveLocation(location: SavedLocation) {
        prefs.edit()
            .putString(KEY_CITY_NAME, location.name)
            .putString(KEY_LATITUDE, location.latitude.toString())
            .putString(KEY_LONGITUDE, location.longitude.toString())
            .apply()
    }

    fun clearLocation() {
        prefs.edit()
            .remove(KEY_CITY_NAME)
            .remove(KEY_LATITUDE)
            .remove(KEY_LONGITUDE)
            .apply()
    }

    fun getForecastHigh(locationKey: String, date: String): Int? {
        if (prefs.getString("nws_high_location", null) != locationKey ||
            prefs.getString("nws_high_date", null) != date || !prefs.contains("nws_high_temp")) return null
        return prefs.getInt("nws_high_temp", 0)
    }

    fun saveForecastHigh(locationKey: String, date: String, high: Int) {
        prefs.edit()
            .putString("nws_high_location", locationKey)
            .putString("nws_high_date", date)
            .putInt("nws_high_temp", high)
            .apply()
    }
}
