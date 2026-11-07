package com.enil.logez.feature.workout.finish

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression from the M4c review: backdating landed on the wrong local day for anyone not on UTC,
 * because the picker's UTC-midnight millis were mixed with the workout instant using raw `%
 * DAY_MILLIS` arithmetic while the row above rendered in the device zone.
 */
class FinishDatePickerConversionTest {
    private val manila: ZoneId = ZoneId.of("Asia/Manila") // UTC+8, no DST
    private val losAngeles: ZoneId = ZoneId.of("America/Los_Angeles") // UTC-7/-8, has DST

    @Test
    fun `the picker opens on the same day the screen displays, east of UTC`() {
        // 01:30 local on 24 Aug is still 23 Aug in UTC — the case that used to mismatch.
        val startedAt = localMillis("2026-08-24T01:30", manila)

        val picker = toDatePickerMillis(startedAt, manila)

        assertEquals(LocalDate.of(2026, 8, 24), utcDateOf(picker))
    }

    @Test
    fun `the picker opens on the same day the screen displays, west of UTC`() {
        // 18:00 local on 23 Aug is already 24 Aug in UTC.
        val startedAt = localMillis("2026-08-23T18:00", losAngeles)

        val picker = toDatePickerMillis(startedAt, losAngeles)

        assertEquals(LocalDate.of(2026, 8, 23), utcDateOf(picker))
    }

    @Test
    fun `moving back two days moves back exactly two days and keeps the time`() {
        val startedAt = localMillis("2026-08-24T01:30", manila)
        val picked = utcMidnight("2026-08-22")

        val result = fromDatePickerMillis(picked, startedAt, manila)

        assertEquals(localMillis("2026-08-22T01:30", manila), result)
    }

    @Test
    fun `a round trip through the picker leaves the instant untouched`() {
        listOf(
            "2026-08-24T01:30" to manila,
            "2026-08-23T18:00" to losAngeles,
            "2026-01-01T00:00" to manila,
            "2026-12-31T23:59" to losAngeles,
        ).forEach { (local, zone) ->
            val startedAt = localMillis(local, zone)
            val roundTripped = fromDatePickerMillis(toDatePickerMillis(startedAt, zone), startedAt, zone)
            assertEquals("round trip changed $local in $zone", startedAt, roundTripped)
        }
    }

    @Test
    fun `re-dating across a daylight-saving boundary keeps the wall-clock time`() {
        // US DST ended 1 Nov 2026; moving a 10:00 session from November back into October must
        // still read 10:00, not 09:00, even though the UTC offset changed underneath it.
        val startedAt = localMillis("2026-11-10T10:00", losAngeles)

        val result = fromDatePickerMillis(utcMidnight("2026-10-10"), startedAt, losAngeles)

        assertEquals(localMillis("2026-10-10T10:00", losAngeles), result)
    }

    private fun localMillis(isoLocal: String, zone: ZoneId): Long =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

    private fun utcMidnight(isoDate: String): Long =
        LocalDate.parse(isoDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    private fun utcDateOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
}
