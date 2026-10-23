package com.example.clockbrief.data

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ForecastSourceTest {
    private val now = Instant.parse("2026-10-23T00:00:00Z")

    @Test fun convertsMeasuredAndForecastUnitsWithoutInventingMissingValues() {
        assertEquals(71, temperatureF(21.6666667, "wmoUnit:degC"))
        assertEquals(47, temperatureF(47.0, "F"))
        assertEquals(32, temperatureF(0.0, "C"))
        assertNull(temperatureF(null, "C"))
        assertNull(temperatureF(Double.NaN, "C"))
        assertNull(temperatureF(Double.POSITIVE_INFINITY, "F"))
        assertNull(temperatureF(273.0, "unknown"))
    }

    @Test fun acceptsRecentObservationsAndRejectsOldOrFutureReadings() {
        fun observation(time: Instant) = StationObservation(71, "Clear", time, "KCHA")
        assertTrue(observation(now.minusSeconds(5_400)).isRecent(now))
        assertFalse(observation(now.minusSeconds(5_401)).isRecent(now))
        assertTrue(observation(now.plusSeconds(300)).isRecent(now))
        assertFalse(observation(now.plusSeconds(301)).isRecent(now))
    }

    @Test fun stationDistanceExcludesDistantAirports() {
        assertTrue(distanceKm(35.2359, -85.19079, 35.0353, -85.2038) < 50)
        assertTrue(distanceKm(35.2359, -85.19079, 35.9513, -85.0851) > 50)
    }

    @Test fun displaysPublishedForecastEvenWithoutCurrentObservationOrTodaysHigh() {
        assertEquals("71° · Clear  (75° / 47°)", WeatherData(71, "Clear", 75, 47, "").displayLine())
        assertEquals("Current unavailable  (75° / 47°)", WeatherData(null, "", 75, 47, "").displayLine())
        assertEquals("71° · Clear  (— / 47°)", WeatherData(71, "Clear", null, 47, "").displayLine())
        assertEquals("71°", WeatherData(71, "", null, null, "").displayLine())
    }

    @Test fun showsObservationAgeSeparatelyFromRefreshAge() {
        val state = WeatherRefreshState(
            location = SavedLocation("City", 0.0, 0.0),
            weather = WeatherData(71, "Clear", 75, 47, "", observedAtMillis = 0),
            lastSuccessMillis = 3_000_000
        )
        assertTrue(state.statusText(3_000_000).contains("Updated just now"))
        assertTrue(state.statusText(3_000_000).contains("Observed 50 min ago"))
    }

    @Test fun marksMissingCurrentObservationWhileForecastsAreAvailable() {
        val state = WeatherRefreshState(
            location = SavedLocation("City", 0.0, 0.0),
            weather = WeatherData(null, "", 75, 47, ""),
            lastSuccessMillis = 0
        )
        assertTrue(state.statusText(0).contains("Current observations unavailable"))
    }

    @Test fun describesAirportWeatherWithoutExposingReportCodes() {
        assertEquals("Rain", aviationCondition("-RA", "OVC"))
        assertEquals("Thunderstorm", aviationCondition("TSRA", "BKN"))
        assertEquals("Clear", aviationCondition("", "CLR"))
        assertEquals("Mostly Cloudy", aviationCondition("", "BKN"))
        assertEquals("", aviationCondition("", "unknown"))
    }
}
