package com.example.clockbrief.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

internal data class WeatherRefreshState(
    val location: SavedLocation? = null,
    val weather: WeatherData? = null,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false,
    val lastSuccessMillis: Long? = null
) {
    fun statusText(nowMillis: Long): String {
        if (location == null) return ""
        val ageMinutes = lastSuccessMillis?.let { ((nowMillis - it).coerceAtLeast(0) / 60_000) }
        val updated = when {
            ageMinutes == null -> ""
            ageMinutes == 0L -> "Updated just now"
            ageMinutes < 60 -> "Updated $ageMinutes min ago"
            ageMinutes < 1440 -> "Updated ${ageMinutes / 60} hr ago"
            else -> "Updated ${ageMinutes / 1440} days ago"
        }
        val message = when {
            hasError && weather == null -> "Weather unavailable · Retrying automatically"
            hasError -> "Weather may be outdated · Retrying automatically"
            isRefreshing && weather == null -> "Loading weather…"
            ageMinutes != null && ageMinutes >= 15 -> "Weather may be outdated"
            isRefreshing -> "Updating weather…"
            else -> ""
        }
        return listOf(updated, message).filter { it.isNotEmpty() }.joinToString("\n")
    }
}

/** Owned by MainActivity and called on its main thread; no polling while stopped. */
internal class WeatherRefreshController(
    private val fetchWeather: suspend (SavedLocation) -> Result<WeatherData>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val log: (String) -> Unit = {}
) {
    companion object {
        const val REFRESH_INTERVAL_MS = 5 * 60_000L
        private const val INITIAL_RETRY_MS = 60_000L
    }

    private val selectedLocation = MutableStateFlow<SavedLocation?>(null)
    private val mutableState = MutableStateFlow(WeatherRefreshState())
    val state = mutableState.asStateFlow()

    fun selectLocation(location: SavedLocation?) {
        if (selectedLocation.value == location) return
        // Clear the old city's data immediately, before a new request can suspend.
        mutableState.value = WeatherRefreshState(location = location, isRefreshing = location != null)
        selectedLocation.value = location
    }

    suspend fun runWhileStarted() {
        selectedLocation.collectLatest { location ->
            if (location == null) return@collectLatest
            var retryDelay = INITIAL_RETRY_MS
            try {
                while (currentCoroutineContext().isActive) {
                    mutableState.value = mutableState.value.copy(isRefreshing = true)
                    val startedAt = elapsedMillis()
                    log("Refresh started")
                    val result = try {
                        fetchWeather(location)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    // Blocking HTTP may finish after cancellation or after a city switch.
                    currentCoroutineContext().ensureActive()
                    if (selectedLocation.value != location) return@collectLatest
                    val failure = result.exceptionOrNull()
                    if (failure is CancellationException) throw failure
                    val duration = (elapsedMillis() - startedAt).coerceAtLeast(0)
                    val waitMillis = if (result.isSuccess) {
                        mutableState.value = WeatherRefreshState(
                            location = location,
                            weather = result.getOrThrow(),
                            lastSuccessMillis = nowMillis()
                        )
                        log("Refresh succeeded in ${duration}ms")
                        retryDelay = INITIAL_RETRY_MS
                        // Schedule from request start so network time does not accumulate.
                        (REFRESH_INTERVAL_MS - duration).coerceAtLeast(1_000)
                    } else {
                        mutableState.value = mutableState.value.copy(isRefreshing = false, hasError = true)
                        // Never log the exception message or URL: they can contain coordinates.
                        log("Refresh failed (${failure?.javaClass?.simpleName}); retry in ${retryDelay / 1000}s")
                        val wait = retryDelay
                        retryDelay = (retryDelay * 2).coerceAtMost(REFRESH_INTERVAL_MS)
                        wait
                    }
                    delay(waitMillis)
                }
            } finally {
                if (mutableState.value.location == location) {
                    mutableState.value = mutableState.value.copy(isRefreshing = false)
                }
            }
        }
    }
}
