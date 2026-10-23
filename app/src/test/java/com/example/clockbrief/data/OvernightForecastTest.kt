package com.example.clockbrief.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class OvernightForecastTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.parse("2026-10-22")
    private fun period(start: String, daytime: Boolean, temp: Int?) =
        ForecastPeriod(OffsetDateTime.parse(start).toInstant(), daytime, temp)
    private fun maximum(start: String, temp: Int?) =
        ForecastMaximum(OffsetDateTime.parse(start).toInstant(), temp)

    @Test fun pairsTodaysHighWithTheNightBeginningToday() {
        val periods = listOf(
            period("2026-10-22T00:00-04:00", false, 39),
            period("2026-10-22T08:00-04:00", true, 75),
            period("2026-10-22T18:00-04:00", false, 47),
            period("2026-10-23T06:00-04:00", true, 72),
            period("2026-10-23T18:00-04:00", false, 44)
        )
        assertEquals(75, todaysHighF(emptyList(), periods, today, zone))
        assertEquals(47, overnightLowF(periods, today, zone))
    }

    @Test fun retainsTodaysGridHighWhenDaytimeTextIsGone() {
        val periods = listOf(period("2026-10-23T06:00-04:00", true, 72))
        val maxima = listOf(maximum("2026-10-22T17:00Z", 75), maximum("2026-10-23T12:00Z", 72))
        assertEquals(75, todaysHighF(maxima, periods, today, zone))
        assertNull(todaysHighF(emptyList(), periods, today, zone))
    }

    @Test fun usesNewGridHighBeforeOlderDaytimeText() {
        assertEquals(75, todaysHighF(listOf(maximum("2026-10-22T17:00Z", 75)),
            listOf(period("2026-10-22T08:00-04:00", true, 74)), today, zone))
    }

    @Test fun missingTonightValueDoesNotBecomeTomorrowNightsLow() {
        assertNull(overnightLowF(listOf(
            period("2026-10-22T18:00-04:00", false, null),
            period("2026-10-23T18:00-04:00", false, 44)), today, zone))
    }

    @Test fun usesPublishedLowAcrossBothClockChangeNights() {
        for ((date, start) in listOf(
            "2026-03-07" to "2026-03-07T18:00-05:00",
            "2026-10-31" to "2026-10-31T18:00-04:00",
            "2027-11-06" to "2027-11-06T18:00-04:00"
        )) {
            assertEquals(47, overnightLowF(listOf(period(start, false, 47)), LocalDate.parse(date), zone))
        }
    }

    @Test fun handlesYearRolloverWithoutSelectingNextDaysForecast() {
        val date = LocalDate.parse("2026-12-31")
        val periods = listOf(
            period("2026-12-31T18:00-05:00", false, 30),
            period("2027-01-01T18:00-05:00", false, 20))
        assertEquals(30, overnightLowF(periods, date, zone))
    }

    @Test fun usesForecastLocationDateRatherThanUtcOrDeviceDate() {
        assertEquals(47, overnightLowF(listOf(period("2026-10-23T01:00Z", false, 47)), today, zone))
        assertEquals(75, todaysHighF(listOf(maximum("2026-10-23T00:00Z", 75)),
            emptyList(), today, ZoneId.of("America/Los_Angeles")))
    }

    @Test fun doesNotInventTemperaturesWhenForecastIsUnavailable() {
        assertNull(todaysHighF(emptyList(), emptyList(), today, zone))
        assertNull(overnightLowF(emptyList(), today, zone))
        assertNull(overnightLowF(listOf(period("2026-10-22T06:00-04:00", false, 39)), today, zone))
    }
}
