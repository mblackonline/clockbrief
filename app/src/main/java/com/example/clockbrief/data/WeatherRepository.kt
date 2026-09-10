package com.example.clockbrief.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherData(
    val currentTempF: Int,
    val condition: String,
    val highTempF: Int?,
    val lowTempF: Int?,
    val lastUpdated: String
)

data class GeocodingResult(
    val name: String,
    val adminArea: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    val displayLabel: String
        get() = buildString {
            append(name)
            if (!adminArea.isNullOrBlank()) append(", $adminArea")
            if (!country.isNullOrBlank()) append(", $country")
        }
}

class WeatherRepository {

    suspend fun searchCity(query: String): Result<List<GeocodingResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString =
                "https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=5&language=en&format=json"
            val responseString = httpGet(urlString)
            val json = JSONObject(responseString)

            val results = mutableListOf<GeocodingResult>()
            if (json.has("results")) {
                val array = json.getJSONArray("results")
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    results.add(
                        GeocodingResult(
                            name = item.optString("name"),
                            adminArea = item.optString("admin1").takeIf { it.isNotBlank() },
                            country = item.optString("country").takeIf { it.isNotBlank() },
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude")
                        )
                    )
                }
            }
            results
        }
    }

    suspend fun fetchWeather(latitude: Double, longitude: Double): Result<WeatherData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val urlString =
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                            "&current=temperature_2m,weather_code" +
                            "&daily=temperature_2m_max,temperature_2m_min" +
                            "&temperature_unit=fahrenheit" +
                            "&timezone=auto&forecast_days=1"

                val responseString = httpGet(urlString)
                val json = JSONObject(responseString)

                val currentObj = json.getJSONObject("current")
                val currentTemp = currentObj.getDouble("temperature_2m").roundToInt()
                val weatherCode = currentObj.getInt("weather_code")

                var highTemp: Int? = null
                var lowTemp: Int? = null
                if (json.has("daily")) {
                    val dailyObj = json.getJSONObject("daily")
                    val maxArr = dailyObj.optJSONArray("temperature_2m_max")
                    val minArr = dailyObj.optJSONArray("temperature_2m_min")
                    if (maxArr != null && maxArr.length() > 0) {
                        highTemp = maxArr.getDouble(0).roundToInt()
                    }
                    if (minArr != null && minArr.length() > 0) {
                        lowTemp = minArr.getDouble(0).roundToInt()
                    }
                }

                val lastUpdated = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

                WeatherData(
                    currentTempF = currentTemp,
                    condition = parseWeatherCode(weatherCode),
                    highTempF = highTemp,
                    lowTempF = lowTemp,
                    lastUpdated = lastUpdated
                )
            }
        }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "ClockBrief/1.0 (Android)")

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP error $responseCode from $urlString")
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63 -> "Rain"
            65 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71, 73 -> "Snow"
            75 -> "Heavy Snow"
            77 -> "Snow Grains"
            80, 81 -> "Showers"
            82 -> "Heavy Showers"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with Hail"
            else -> "Fair"
        }
    }
}
