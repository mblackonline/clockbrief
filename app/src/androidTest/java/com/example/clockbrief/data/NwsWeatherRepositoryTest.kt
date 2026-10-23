package com.example.clockbrief.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Android JSON parsing with deterministic National Weather Service responses. */
@RunWith(AndroidJUnit4::class)
class NwsWeatherRepositoryTest {
    private val clock = Instant.parse("2026-10-22T23:30:00Z")
    private val pointUrl = "https://api.weather.gov/points/35.2359,-85.19079"
    private val gridUrl = "https://api.weather.gov/gridpoints/MRX/27,15"
    private val stationsUrl = "$gridUrl/stations"
    private val forecastUrl = "$gridUrl/forecast"
    private val firstObservation = "https://api.weather.gov/stations/KCHA/observations/latest"
    private val secondObservation = "https://api.weather.gov/stations/KRZR/observations/latest"
    private val aviationUrl = "https://aviationweather.gov/api/data/metar?ids=KCHA,KRZR&format=json"

    @Test fun readsMeasuredCurrentAndPublishedDayNightForecasts() = runBlocking {
        val responses = fixtures()
        val requests = mutableListOf<String>()
        val weather = repository(responses, requests).fetchWeather(35.2359, -85.19079).getOrThrow()
        assertEquals(71, weather.currentTempF)
        assertEquals("Clear", weather.condition)
        assertEquals(75, weather.highTempF)
        assertEquals(47, weather.lowTempF)
        assertEquals("KCHA", weather.observationStation)
        assertEquals(clock.minusSeconds(600).toEpochMilli(), weather.observedAtMillis)
        assertTrue(requests.all { it.startsWith("https://api.weather.gov/") })
        assertFalse(requests.any { "KFAR" in it })
    }

    @Test fun triesNextNearbyStationWhenFirstObservationIsStale() = runBlocking {
        val responses = fixtures().apply { this[firstObservation] = observation(clock.minusSeconds(36_000), 11.0) }
        val weather = repository(responses).fetchWeather(35.2359, -85.19079).getOrThrow()
        assertEquals(70, weather.currentTempF)
        assertEquals("KRZR", weather.observationStation)
    }

    @Test fun missingTemperatureOrStationFailureDoesNotBecomeZeroDegrees() = runBlocking {
        for (missing in listOf<String?>(observation(clock, null), null)) {
            val responses = fixtures()
            if (missing == null) responses.remove(firstObservation) else responses[firstObservation] = missing
            val weather = repository(responses).fetchWeather(35.2359, -85.19079).getOrThrow()
            assertEquals(70, weather.currentTempF)
        }
    }

    @Test fun rejectsStationReadingWithFutureTimestamp() = runBlocking {
        val responses = fixtures().apply { this[firstObservation] = observation(clock.plusSeconds(600), 11.0) }
        assertEquals("KRZR", repository(responses).fetchWeather(35.2359, -85.19079).getOrThrow().observationStation)
    }

    @Test fun usesFreshAviationWeatherCenterReadingWhenMainFeedIsStale() = runBlocking {
        val responses = fixtures().apply {
            this[firstObservation] = observation(clock.minusSeconds(36_000), 11.0)
            this[secondObservation] = observation(clock.minusSeconds(36_000), 7.0)
            // Deliberately reversed: station distance, not array order, determines selection.
            this[aviationUrl] = JSONArray().put(airport("KRZR", 13.0, clock.minusSeconds(300)))
                .put(airport("KCHA", 16.1, clock.minusSeconds(300))).toString()
        }
        val weather = repository(responses).fetchWeather(35.2359, -85.19079).getOrThrow()
        assertEquals(61, weather.currentTempF)
        assertEquals("KCHA", weather.observationStation)
        assertEquals("Clear", weather.condition)
    }

    @Test fun keepsForecastsButDoesNotDisplayStaleOrUnrelatedAirportTemperature() = runBlocking {
        val responses = fixtures().apply {
            this[firstObservation] = observation(clock.minusSeconds(36_000), 11.0)
            this[secondObservation] = observation(clock.minusSeconds(36_000), 7.0)
            this[aviationUrl] = JSONArray().put(airport("KCHA", 16.1, clock.minusSeconds(36_000)))
                .put(airport("KFAR", 25.0, clock)).toString()
        }
        val weather = repository(responses).fetchWeather(35.2359, -85.19079).getOrThrow()
        assertNull(weather.currentTempF)
        assertNull(weather.observedAtMillis)
        assertEquals(75, weather.highTempF)
        assertEquals(47, weather.lowTempF)
        assertTrue(weather.displayLine().startsWith("Current unavailable"))
    }

    @Test fun fallsBackToDaytimeTextWhenGridEndpointFails() = runBlocking {
        val responses = fixtures().apply { remove(gridUrl) }
        assertEquals(74, repository(responses).fetchWeather(35.2359, -85.19079).getOrThrow().highTempF)
    }

    @Test fun preservesTodaysHighAcrossRepositoryRestartButNeverUsesItForAnotherDate() = runBlocking {
        val stored = mutableMapOf<Pair<String, String>, Int>()
        val responses = fixtures()
        fun repo(at: Instant) = WeatherRepository(
            request = { responses[it] ?: throw IOException("fixture unavailable") },
            now = { at },
            readHigh = { key, date -> stored[key to date] },
            writeHigh = { key, date, high -> stored[key to date] = high }
        )
        assertEquals(75, repo(clock).fetchWeather(35.2359, -85.19079).getOrThrow().highTempF)
        responses.remove(gridUrl)
        responses[forecastUrl] = forecast(listOf(
            period("2026-10-22T18:00:00-04:00", false, 47),
            period("2026-10-23T18:00:00-04:00", false, 44)))
        assertEquals(75, repo(clock).fetchWeather(35.2359, -85.19079).getOrThrow().highTempF)
        val nextDay = repo(Instant.parse("2026-10-23T18:00:00Z"))
            .fetchWeather(35.2359, -85.19079).getOrThrow()
        assertNull(nextDay.highTempF)
        assertEquals(44, nextDay.lowTempF)
    }

    @Test fun cachesLocationMetadataWithoutCachingTheActualWeather() = runBlocking {
        val requests = mutableListOf<String>()
        val repo = repository(fixtures(), requests)
        repeat(2) { repo.fetchWeather(35.2359, -85.19079).getOrThrow() }
        assertEquals(1, requests.count { it == pointUrl })
        assertEquals(1, requests.count { it == stationsUrl })
        assertEquals(2, requests.count { it == forecastUrl })
        assertEquals(2, requests.count { it == firstObservation })
    }

    @Test fun unavailableForecastReturnsFailureWithoutUsingAnotherWeatherProvider() = runBlocking {
        val requests = mutableListOf<String>()
        val result = repository(fixtures().apply { remove(forecastUrl) }, requests)
            .fetchWeather(35.2359, -85.19079)
        assertTrue(result.isFailure)
        assertTrue(requests.all { it.startsWith("https://api.weather.gov/") })
    }

    @Test fun cancellationIsNotSwallowedByOptionalGridOrObservationHandling() {
        val responses = fixtures()
        val repo = WeatherRepository(request = {
            if (it == gridUrl) throw CancellationException()
            responses[it] ?: throw IOException("fixture unavailable")
        }, now = { clock })
        try {
            runBlocking { repo.fetchWeather(35.2359, -85.19079) }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation must propagate to the existing refresh controller.
        }
    }

    private fun repository(responses: Map<String, String>, requests: MutableList<String> = mutableListOf()) =
        WeatherRepository(request = {
            requests += it
            responses[it] ?: throw IOException("fixture unavailable")
        }, now = { clock })

    private fun fixtures(): MutableMap<String, String> = mutableMapOf(
        pointUrl to properties(JSONObject().put("timeZone", "America/New_York")
            .put("forecast", forecastUrl).put("forecastGridData", gridUrl).put("observationStations", stationsUrl)),
        stationsUrl to JSONObject().put("features", JSONArray()
            .put(station("KCHA", -85.2038, 35.0353)).put(station("KRZR", -84.7973, 35.2115))
            .put(station("KFAR", -84.0, 36.0))).toString(),
        forecastUrl to forecast(listOf(
            period("2026-10-22T00:00:00-04:00", false, 39),
            period("2026-10-22T08:00:00-04:00", true, 74),
            period("2026-10-22T18:00:00-04:00", false, 47),
            period("2026-10-23T06:00:00-04:00", true, 72),
            period("2026-10-23T18:00:00-04:00", false, 44))),
        gridUrl to properties(JSONObject().put("maxTemperature", JSONObject().put("uom", "wmoUnit:degC")
            .put("values", JSONArray().put(JSONObject().put("validTime", "2026-10-22T17:00:00+00:00/PT8H")
                .put("value", 23.8888888889))))),
        firstObservation to observation(clock.minusSeconds(600), 21.6666667),
        secondObservation to observation(clock.minusSeconds(600), 21.1111111),
        aviationUrl to "[]"
    )

    private fun properties(value: JSONObject) = JSONObject().put("properties", value).toString()
    private fun forecast(periods: List<JSONObject>) = properties(JSONObject().put("periods", JSONArray(periods)))
    private fun period(start: String, daytime: Boolean, temp: Int) = JSONObject()
        .put("startTime", start).put("isDaytime", daytime).put("temperature", temp).put("temperatureUnit", "F")
    private fun station(id: String, lon: Double, lat: Double) = JSONObject()
        .put("properties", JSONObject().put("stationIdentifier", id))
        .put("geometry", JSONObject().put("coordinates", JSONArray().put(lon).put(lat)))
    private fun observation(time: Instant, temp: Double?) = properties(JSONObject()
        .put("timestamp", time.toString()).put("textDescription", "Clear")
        .put("temperature", JSONObject().put("value", temp ?: JSONObject.NULL).put("unitCode", "wmoUnit:degC")))
    private fun airport(id: String, temp: Double, time: Instant) = JSONObject()
        .put("icaoId", id).put("temp", temp).put("obsTime", time.epochSecond).put("cover", "CLR")
}
