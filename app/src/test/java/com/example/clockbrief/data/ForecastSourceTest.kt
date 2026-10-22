package com.example.clockbrief.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ForecastSourceTest {
    private val baseUrl = "https://api.open-meteo.com/v1/forecast?latitude=35.2359&longitude=-85.19079"

    @Test fun nbmHighLowDoesNotOverrideDefaultCurrentConditions() {
        val requests = mutableListOf<String>()
        val responses = fetchWeatherResponses(baseUrl) { url ->
            requests += url
            when {
                "&current=" in url && "&models=" !in url -> "default current conditions"
                "&current=" !in url && "&models=ncep_nbm_conus" in url -> "nbm high and low"
                else -> throw AssertionError("Unexpected weather source")
            }
        }
        assertEquals("default current conditions", responses.currentConditions)
        assertEquals("nbm high and low", responses.forecast)
        assertEquals(2, requests.size)
    }

    @Test fun unavailableCurrentConditionsCannotBeReplacedWithNbmCurrentTemperature() {
        val error = IOException("current conditions unavailable")
        val actual = assertThrows(IOException::class.java) {
            fetchWeatherResponses(baseUrl) { url ->
                if ("&current=" in url) throw error
                "nbm forecast"
            }
        }
        assertSame(error, actual)
    }

    @Test fun forecastCoverageFallbackKeepsCurrentConditionsOnDefaultSource() {
        val requests = mutableListOf<String>()
        val responses = fetchWeatherResponses(baseUrl) { url ->
            requests += url
            when {
                "&models=ncep_nbm_conus" in url -> throw WeatherHttpException(400)
                "&current=" in url -> "default current conditions"
                else -> "default forecast"
            }
        }
        assertEquals("default current conditions", responses.currentConditions)
        assertEquals("default forecast", responses.forecast)
        assertEquals(3, requests.size)
        assertEquals(1, requests.count { "&current=" in it })
    }

    @Test fun requestsNbmWithoutAnApiKey() {
        val requests = mutableListOf<String>()
        val response = fetchForecastResponse(baseUrl) { url ->
            requests += url
            "nbm forecast"
        }
        assertEquals("nbm forecast", response)
        assertEquals(listOf("$baseUrl&models=ncep_nbm_conus"), requests)
    }

    @Test fun usesDefaultModelWhenNbmDoesNotCoverTheLocation() {
        val requests = mutableListOf<String>()
        val response = fetchForecastResponse(baseUrl) { url ->
            requests += url
            if (requests.size == 1) throw WeatherHttpException(400)
            "default forecast"
        }
        assertEquals("default forecast", response)
        assertEquals(listOf("$baseUrl&models=ncep_nbm_conus", baseUrl), requests)
    }

    @Test fun doesNotHideServerErrorsOrMakeExtraRequests() {
        var calls = 0
        val error = WeatherHttpException(503)
        val actual = assertThrows(WeatherHttpException::class.java) {
            fetchForecastResponse(baseUrl) { calls++; throw error }
        }
        assertSame(error, actual)
        assertEquals(1, calls)
    }

    @Test fun preservesNetworkFailureAndCancellation() {
        for (error in listOf(IOException("offline"), CancellationException())) {
            var calls = 0
            val actual = assertThrows(error.javaClass) {
                fetchForecastResponse(baseUrl) { calls++; throw error }
            }
            assertSame(error, actual)
            assertEquals(1, calls)
        }
    }

    @Test fun propagatesFallbackFailureWithoutRetryingAgain() {
        var calls = 0
        val actual = assertThrows(WeatherHttpException::class.java) {
            fetchForecastResponse(baseUrl) {
                calls++
                throw WeatherHttpException(400)
            }
        }
        assertEquals(400, actual.statusCode)
        assertEquals(2, calls)
    }
}
