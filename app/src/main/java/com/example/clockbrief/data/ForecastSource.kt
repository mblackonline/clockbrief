package com.example.clockbrief.data

import java.io.IOException
import java.net.HttpURLConnection

internal class WeatherHttpException(val statusCode: Int) : IOException("HTTP error $statusCode")

internal data class WeatherResponses(val currentConditions: String, val forecast: String)

/** Model selection for high/low forecasts must never change the current conditions source. */
internal fun fetchWeatherResponses(baseUrl: String, request: (String) -> String): WeatherResponses {
    val forecast = fetchForecastResponse(
        "$baseUrl&daily=temperature_2m_max&hourly=temperature_2m&forecast_days=2",
        request
    )
    val current = request("$baseUrl&current=temperature_2m,weather_code")
    return WeatherResponses(currentConditions = current, forecast = forecast)
}

/** Prefer NOAA's blend; Open-Meteo returns HTTP 400 outside its coverage. */
internal fun fetchForecastResponse(baseUrl: String, request: (String) -> String): String = try {
    request("$baseUrl&models=ncep_nbm_conus")
} catch (error: WeatherHttpException) {
    if (error.statusCode != HttpURLConnection.HTTP_BAD_REQUEST) throw error
    request(baseUrl)
}
