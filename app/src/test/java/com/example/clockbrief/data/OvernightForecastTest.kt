package com.example.clockbrief.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OvernightForecastTest {
    private val eastern = ZoneId.of("America/New_York")

    @Test fun usesTonightAcrossMidnightAndExcludesPreviousMorningAndTomorrowDaytime() {
        assertEquals(48, low("2026-10-22",
            "2026-10-22T05:00-04:00" to 35.0,
            "2026-10-22T20:00-04:00" to 60.0,
            "2026-10-22T23:00-04:00" to 54.0,
            "2026-10-23T06:00-04:00" to 48.4,
            "2026-10-23T08:00-04:00" to 49.0,
            "2026-10-23T15:00-04:00" to 30.0
        ))
    }

    @Test fun canUseAnEveningMinimumBeforeMidnight() {
        assertEquals(44, low("2026-10-22",
            "2026-10-22T23:00-04:00" to 43.6,
            "2026-10-23T06:00-04:00" to 51.0
        ))
    }

    @Test fun daylightSavingWindowIncludesEightPmAndEightAmButExcludesOutsideHours() {
        assertEquals(49, low("2026-10-22",
            "2026-10-22T19:00-04:00" to 20.0,
            "2026-10-22T20:00-04:00" to 50.0,
            "2026-10-23T07:00-04:00" to 50.4,
            "2026-10-23T08:00-04:00" to 49.4,
            "2026-10-23T09:00-04:00" to 30.0
        ))
        assertEquals(40, low("2026-10-22",
            "2026-10-22T20:00-04:00" to 40.0,
            "2026-10-23T08:00-04:00" to 50.0
        ))
    }

    @Test fun standardTimeWindowIncludesSevenPmAndSevenAmButExcludesOutsideHours() {
        assertEquals(35, low("2026-12-10",
            "2026-12-10T18:00-05:00" to 10.0,
            "2026-12-10T19:00-05:00" to 40.0,
            "2026-12-11T07:00-05:00" to 35.0,
            "2026-12-11T08:00-05:00" to 20.0
        ))
        assertEquals(30, low("2026-12-10",
            "2026-12-10T19:00-05:00" to 30.0,
            "2026-12-11T07:00-05:00" to 40.0
        ))
    }

    @Test fun springClockChangeUsesSevenPmStartAndEightAmFinish() {
        assertEquals(40, low("2026-03-07",
            "2026-03-07T18:00-05:00" to 10.0,
            "2026-03-07T19:00-05:00" to 55.0,
            "2026-03-08T01:00-05:00" to 50.0,
            "2026-03-08T03:00-04:00" to 45.0,
            "2026-03-08T08:00-04:00" to 40.0,
            "2026-03-08T09:00-04:00" to 20.0
        ))
    }

    @Test fun autumnClockChangeUsesEightPmStartAndSevenAmFinish() {
        assertEquals(40, low("2026-10-31",
            "2026-10-31T19:00-04:00" to 10.0,
            "2026-10-31T20:00-04:00" to 55.0,
            "2026-11-01T01:00-04:00" to 50.0,
            "2026-11-01T01:00-05:00" to 45.0,
            "2026-11-01T07:00-05:00" to 40.0,
            "2026-11-01T08:00-05:00" to 20.0
        ))
    }

    @Test fun repeatedAutumnHourKeepsBothForecastSamples() {
        for ((first, second) in listOf(35.0 to 40.0, 40.0 to 35.0)) {
            assertEquals(35, low("2026-10-31",
                "2026-11-01T01:00-04:00" to first,
                "2026-11-01T01:00-05:00" to second
            ))
        }
    }

    @Test fun followsNextYearsDifferentClockChangeDate() {
        assertEquals(40, low("2027-11-01",
            "2027-11-02T07:00-04:00" to 50.0,
            "2027-11-02T08:00-04:00" to 40.0,
            "2027-11-02T09:00-04:00" to 20.0
        ))
        assertEquals(40, low("2027-11-06",
            "2027-11-07T07:00-05:00" to 40.0,
            "2027-11-07T08:00-05:00" to 20.0
        ))
    }

    @Test fun usesTheForecastCityTimeZone() {
        assertEquals(40, low("2026-10-22",
            "2026-10-23T03:00Z" to 55.0,
            "2026-10-23T12:00Z" to 45.0,
            "2026-10-23T15:00Z" to 40.0,
            "2026-10-23T16:00Z" to 20.0,
            zone = ZoneId.of("America/Los_Angeles")
        ))
    }

    @Test fun locationWithoutDaylightSavingKeepsSevenAmInSummer() {
        assertEquals(40, low("2026-07-01",
            "2026-07-01T18:00-07:00" to 20.0,
            "2026-07-01T19:00-07:00" to 50.0,
            "2026-07-02T07:00-07:00" to 40.0,
            "2026-07-02T08:00-07:00" to 10.0,
            zone = ZoneId.of("America/Phoenix")
        ))
    }

    @Test fun handlesMonthAndYearRollover() {
        assertEquals(25, low("2026-12-31",
            "2026-12-31T23:00-05:00" to 30.0,
            "2027-01-01T07:00-05:00" to 25.0,
            "2027-01-01T08:00-05:00" to 10.0
        ))
    }

    @Test fun returnsUnavailableWhenNoOvernightForecastIsPresent() {
        assertNull(low("2026-10-22"))
        assertNull(low("2026-10-22", "2026-10-22T12:00-04:00" to 60.0))
    }

    @Test fun returnsUnavailableWhenHourlyDataIsMissingOrIncomplete() {
        assertNull(overnightLowF(LocalDate.parse("2026-10-22"), eastern,
            listOf(OffsetDateTime.parse("2026-10-22T20:00-04:00").toEpochSecond()), emptyList()))
        for (missing in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(low("2026-10-22",
                "2026-10-22T20:00-04:00" to 60.0,
                "2026-10-23T06:00-04:00" to missing
            ))
        }
    }

    private fun low(
        date: String,
        vararg samples: Pair<String, Double?>,
        zone: ZoneId = eastern
    ): Int? = overnightLowF(
        forecastDate = LocalDate.parse(date),
        timeZone = zone,
        hourlyTimes = samples.map { OffsetDateTime.parse(it.first).toEpochSecond() },
        hourlyTemperaturesF = samples.map { it.second }
    )
}
