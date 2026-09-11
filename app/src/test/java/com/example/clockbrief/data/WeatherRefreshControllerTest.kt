package com.example.clockbrief.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRefreshControllerTest {
    private val cityA = SavedLocation("City A", 0.0, 0.0)
    private val cityB = SavedLocation("City B", 1.0, 1.0)
    private val weather = WeatherData(72, "Clear", 78, 58, "10:00 AM")

    @Test fun fetchesImmediatelyAndEveryFiveMinutesIncludingRequestTime() = runTest {
        val starts = mutableListOf<Long>()
        val controller = WeatherRefreshController(
            fetchWeather = {
                starts += testScheduler.currentTime
                delay(10_000)
                Result.success(weather)
            },
            nowMillis = { testScheduler.currentTime },
            elapsedMillis = { testScheduler.currentTime }
        )
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        assertEquals(listOf(0L), starts)
        advanceTimeBy(299_999)
        runCurrent()
        assertEquals(1, starts.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 300_000L), starts)
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(listOf(0L, 300_000L, 600_000L), starts)
    }

    @Test fun doesNotFetchUntilLocationSelected() = runTest {
        var calls = 0
        val controller = WeatherRefreshController(fetchWeather = { calls++; Result.success(weather) })
        backgroundScope.launch { controller.runWhileStarted() }
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(0, calls)
        controller.selectLocation(cityA)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun stoppingCancelsPollingAndReturningFetchesImmediately() = runTest {
        var calls = 0
        val controller = WeatherRefreshController(fetchWeather = { calls++; Result.success(weather) })
        controller.selectLocation(cityA)
        val first = backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        first.cancelAndJoin()
        advanceTimeBy(900_000)
        runCurrent()
        assertEquals(1, calls)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        assertEquals(2, calls)
    }

    @Test fun failurePreservesSameCityDataAndRecoveryClearsError() = runTest {
        var calls = 0
        val controller = WeatherRefreshController(
            fetchWeather = {
                calls++
                if (calls == 2) Result.failure(IOException("offline")) else Result.success(weather)
            },
            nowMillis = { testScheduler.currentTime },
            elapsedMillis = { testScheduler.currentTime }
        )
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(weather, controller.state.value.weather)
        assertTrue(controller.state.value.hasError)
        assertEquals(0L, controller.state.value.lastSuccessMillis)
        assertTrue(controller.state.value.statusText(300_000).contains("outdated"))
        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(controller.state.value.hasError)
        assertEquals(360_000L, controller.state.value.lastSuccessMillis)
    }

    @Test fun persistentFailuresBackOffToFiveMinutesWithoutLoggingSensitiveMessages() = runTest {
        val starts = mutableListOf<Long>()
        val logs = mutableListOf<String>()
        val controller = WeatherRefreshController(
            fetchWeather = {
                starts += testScheduler.currentTime
                throw IOException("private-coordinate-url")
            },
            elapsedMillis = { testScheduler.currentTime },
            log = logs::add
        )
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        assertTrue(controller.state.value.statusText(0).contains("unavailable"))
        for (interval in listOf(60_000L, 120_000L, 240_000L, 300_000L, 300_000L)) {
            advanceTimeBy(interval)
            runCurrent()
        }
        assertEquals(listOf(0L, 60_000L, 180_000L, 420_000L, 720_000L, 1_020_000L), starts)
        assertTrue(logs.any { it.contains("IOException") })
        assertFalse(logs.any { it.contains("private-coordinate-url") })
    }

    @Test fun switchingCitiesClearsOldWeatherEvenIfNewCityFails() = runTest {
        val controller = WeatherRefreshController(fetchWeather = { location ->
            if (location == cityA) Result.success(weather) else Result.failure(IOException())
        })
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        assertEquals(weather, controller.state.value.weather)
        controller.selectLocation(cityB)
        assertNull(controller.state.value.weather)
        assertNull(controller.state.value.lastSuccessMillis)
        runCurrent()
        assertEquals(cityB, controller.state.value.location)
        assertNull(controller.state.value.weather)
        assertTrue(controller.state.value.hasError)
    }

    @Test fun oldRequestCannotPublishAfterSwitchEvenWhenItIgnoresCancellation() = runTest {
        val controller = WeatherRefreshController(fetchWeather = { location ->
            if (location == cityA) {
                withContext(NonCancellable) { delay(10_000) }
                Result.success(weather)
            } else {
                delay(1_000)
                Result.success(weather.copy(currentTempF = 50))
            }
        })
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        controller.selectLocation(cityB)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(cityB, controller.state.value.location)
        assertNull(controller.state.value.weather)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(50, controller.state.value.weather?.currentTempF)
    }

    @Test fun cancelledRequestDoesNotPublishOrAppearAsNetworkFailure() = runTest {
        val controller = WeatherRefreshController(fetchWeather = {
            withContext(NonCancellable) { delay(1_000) }
            Result.success(weather)
        })
        controller.selectLocation(cityA)
        val job = backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        job.cancel()
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(controller.state.value.weather)
        assertFalse(controller.state.value.hasError)
        assertFalse(controller.state.value.isRefreshing)
    }

    @Test fun cancellationReturnedAsFailureIsNotRetried() = runTest {
        var calls = 0
        val controller = WeatherRefreshController(fetchWeather = {
            calls++
            Result.failure(CancellationException())
        })
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, calls)
        assertFalse(controller.state.value.hasError)
    }

    @Test fun clearingLocationStopsRequestsAndClearsStatus() = runTest {
        var calls = 0
        val controller = WeatherRefreshController(fetchWeather = { calls++; Result.success(weather) })
        controller.selectLocation(cityA)
        backgroundScope.launch { controller.runWhileStarted() }
        runCurrent()
        controller.selectLocation(null)
        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, calls)
        assertNull(controller.state.value.weather)
        assertEquals("", controller.state.value.statusText(600_000))
    }

    @Test fun statusShowsAgeAndMarksDataOlderThanFifteenMinutes() {
        val state = WeatherRefreshState(location = cityA, weather = weather, lastSuccessMillis = 0L)
        assertEquals("Updated just now", state.statusText(0))
        assertEquals("Updated 5 min ago", state.statusText(300_000))
        assertTrue(state.statusText(900_000).contains("outdated"))
        assertTrue(state.statusText(86_400_000).contains("1 days ago"))
    }
}
