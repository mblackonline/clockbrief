package com.example.clockbrief.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** NOAA verification window: 7 PM through 7 AM in the forecast city's standard time. */
internal fun overnightLowF(
    forecastDate: LocalDate,
    timeZone: ZoneId,
    hourlyTimes: List<Long>,
    hourlyTemperaturesF: List<Double?>
): Int? {
    if (hourlyTimes.size != hourlyTemperaturesF.size) return null

    val start = standardTimeEpochSecond(forecastDate, 19, timeZone)
    val end = standardTimeEpochSecond(forecastDate.plusDays(1), 7, timeZone)
    var minimum: Double? = null
    for (index in hourlyTimes.indices) {
        val time = hourlyTimes[index]
        if (time < start || time > end) continue
        // Do not report a misleading minimum if an overnight sample is missing.
        val temperature = hourlyTemperaturesF[index]?.takeIf { it.isFinite() } ?: return null
        minimum = minimum?.let { minOf(it, temperature) } ?: temperature
    }
    return minimum?.roundToInt()
}

private fun standardTimeEpochSecond(date: LocalDate, hour: Int, timeZone: ZoneId): Long {
    val localTime = date.atTime(hour, 0)
    val standardOffset = timeZone.rules.getStandardOffset(localTime.atZone(timeZone).toInstant())
    return localTime.toEpochSecond(standardOffset)
}
