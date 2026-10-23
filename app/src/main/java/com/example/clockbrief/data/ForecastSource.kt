package com.example.clockbrief.data

import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.*

internal class WeatherHttpException(val statusCode: Int) : IOException("HTTP error $statusCode")

internal data class StationObservation(
    val temperatureF: Int,
    val description: String,
    val observedAt: Instant,
    val stationId: String
)

internal fun StationObservation.isRecent(now: Instant): Boolean {
    val age = Duration.between(observedAt, now)
    return age >= Duration.ofMinutes(-5) && age <= Duration.ofMinutes(90)
}

internal fun temperatureF(value: Double?, unit: String): Int? {
    if (value == null || !value.isFinite()) return null
    val fahrenheit = when (unit) {
        "C", "wmoUnit:degC" -> value * 9.0 / 5.0 + 32.0
        "F", "wmoUnit:degF" -> value
        else -> return null
    }
    return fahrenheit.roundToInt()
}

internal fun aviationCondition(weather: String, sky: String): String = when {
    "TS" in weather -> "Thunderstorm"
    "SN" in weather -> "Snow"
    "RA" in weather -> "Rain"
    "DZ" in weather -> "Drizzle"
    "FG" in weather -> "Fog"
    "BR" in weather -> "Mist"
    else -> when (sky) {
        "CLR", "SKC", "CAVOK" -> "Clear"
        "FEW" -> "Mainly Clear"
        "SCT" -> "Partly Cloudy"
        "BKN" -> "Mostly Cloudy"
        "OVC" -> "Overcast"
        else -> ""
    }
}

internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val latitudeDelta = Math.toRadians(lat2 - lat1)
    val longitudeDelta = Math.toRadians(lon2 - lon1)
    val a = sin(latitudeDelta / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(longitudeDelta / 2).pow(2)
    return 6371.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
