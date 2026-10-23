package com.example.clockbrief.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ForecastPeriod(
    val start: Instant,
    val isDaytime: Boolean,
    val temperatureF: Int?
)

internal data class ForecastMaximum(val start: Instant, val temperatureF: Int?)

/** Use the published low for the night beginning today, not the preceding overnight period. */
internal fun overnightLowF(periods: List<ForecastPeriod>, today: LocalDate, zone: ZoneId): Int? =
    periods.filter { period ->
        val start = period.start.atZone(zone)
        !period.isDaytime && start.toLocalDate() == today && start.hour >= 12
    }.minByOrNull { it.start }?.temperatureF

internal fun todaysHighF(
    maxima: List<ForecastMaximum>,
    periods: List<ForecastPeriod>,
    today: LocalDate,
    zone: ZoneId
): Int? {
    // The grid can retain today's maximum after the daytime text period disappears.
    return maxima.filter { it.start.atZone(zone).toLocalDate() == today }
        .mapNotNull { it.temperatureF }.maxOrNull()
        ?: periods.filter { it.isDaytime && it.start.atZone(zone).toLocalDate() == today }
            .mapNotNull { it.temperatureF }.maxOrNull()
}
