package com.example.clockbrief.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OvernightForecastTest {
    private val sunset = "2026-10-22T18:15"
    private val sunrise = "2026-10-23T07:20"

    @Test fun usesTonightAcrossMidnightAndExcludesPreviousMorningAndTomorrowDaytime() {
        assertEquals(48, overnightLowF(
            sunset, sunrise,
            listOf("2026-10-22T05:00", "2026-10-22T19:00", "2026-10-22T23:00",
                "2026-10-23T06:00", "2026-10-23T07:00", "2026-10-23T15:00"),
            listOf(35.0, 60.0, 54.0, 48.4, 49.0, 30.0)
        ))
    }

    @Test fun canUseAnEveningMinimumBeforeMidnight() {
        assertEquals(44, overnightLowF(
            sunset, sunrise,
            listOf("2026-10-22T23:00", "2026-10-23T06:00"),
            listOf(43.6, 51.0)
        ))
    }

    @Test fun excludesHoursBeforeSunsetAndAfterSunrise() {
        assertEquals(55, overnightLowF(
            sunset, sunrise,
            listOf("2026-10-22T18:00", "2026-10-22T19:00", "2026-10-23T08:00"),
            listOf(20.0, 55.0, 10.0)
        ))
    }

    @Test fun includesSamplesExactlyAtSunsetAndSunrise() {
        assertEquals(42, overnightLowF(
            "2026-10-22T18:00", "2026-10-23T07:00",
            listOf("2026-10-22T18:00", "2026-10-23T07:00"),
            listOf(50.0, 42.0)
        ))
    }

    @Test fun handlesMonthAndYearRollover() {
        assertEquals(25, overnightLowF(
            "2026-12-31T17:00", "2027-01-01T07:30",
            listOf("2026-12-31T23:00", "2027-01-01T07:00"),
            listOf(30.0, 25.0)
        ))
    }

    @Test fun returnsUnavailableWhenNightBoundariesAreMissingOrInvalid() {
        for ((start, end) in listOf(null to sunrise, sunset to null, "" to sunrise,
            sunset to "null", sunrise to sunset)) {
            assertNull(overnightLowF(start, end, emptyList(), emptyList()))
        }
    }

    @Test fun returnsUnavailableWhenHourlyDataIsMissingOrIncomplete() {
        assertNull(overnightLowF(sunset, sunrise, emptyList(), emptyList()))
        assertNull(overnightLowF(sunset, sunrise, listOf("2026-10-22T19:00"), emptyList()))
        assertNull(overnightLowF(sunset, sunrise,
            listOf("2026-10-22T19:00", "2026-10-23T06:00"), listOf(60.0, null)))
        assertNull(overnightLowF(sunset, sunrise,
            listOf("2026-10-22T19:00"), listOf(Double.NaN)))
    }
}
