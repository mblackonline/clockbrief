package com.example.clockbrief.data

import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/** Uses forecast-location timestamps, independent of the device's time zone. */
internal fun overnightLowF(
    sunset: String?,
    sunrise: String?,
    hourlyTimes: List<String>,
    hourlyTemperaturesF: List<Double?>
): Int? {
    if (sunset == null || sunrise == null || hourlyTimes.size != hourlyTemperaturesF.size) {
        return null
    }

    return try {
        val start = LocalDateTime.parse(sunset)
        val end = LocalDateTime.parse(sunrise)
        if (end <= start) return null

        var minimum: Double? = null
        for (index in hourlyTimes.indices) {
            val time = LocalDateTime.parse(hourlyTimes[index])
            if (time < start || time > end) continue
            // Do not report a misleading minimum if an overnight sample is missing.
            val temperature = hourlyTemperaturesF[index]?.takeIf { it.isFinite() } ?: return null
            minimum = minimum?.let { minOf(it, temperature) } ?: temperature
        }
        minimum?.roundToInt()
    } catch (_: DateTimeParseException) {
        null
    }
}
