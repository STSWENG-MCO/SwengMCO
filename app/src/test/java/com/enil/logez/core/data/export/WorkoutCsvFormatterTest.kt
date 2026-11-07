package com.enil.logez.core.data.export

import com.enil.logez.core.domain.model.SetType
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The whole class runs under a comma-decimal locale on purpose. A default-locale number format
 * emits `12,5` there and silently corrupts a comma-delimited file — and it is invisible on an
 * English-locale emulator, which is the only place this would otherwise be exercised.
 */
class WorkoutCsvFormatterTest {
    private val original = Locale.getDefault()
    private val utc = ZoneId.of("UTC")

    @Before
    fun setUp() = Locale.setDefault(Locale.GERMANY)

    @After
    fun tearDown() = Locale.setDefault(original)

    private fun row(
        setIndex: Int = 0,
        setType: SetType = SetType.NORMAL,
        weightKg: Double? = 100.0,
        reps: Int? = 5,
        distanceMeters: Double? = null,
        durationSeconds: Int? = null,
        rpe: Double? = null,
        supersetGroup: Int? = null,
        workoutNotes: String? = null,
        exerciseNotes: String? = null,
        endedAt: Long? = 1_766_390_400_000L,
    ) = WorkoutCsvRow(
        workoutTitle = "Push Day",
        workoutNotes = workoutNotes,
        // 2025-12-22T08:00:00Z
        startedAt = 1_766_390_400_000L,
        endedAt = endedAt,
        exerciseName = "Bench Press (Barbell)",
        supersetGroup = supersetGroup,
        exerciseNotes = exerciseNotes,
        setIndex = setIndex,
        setType = setType,
        weightKg = weightKg,
        reps = reps,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        rpe = rpe,
    )

    @Test
    fun `the header is the exact pinned literal`() {
        assertEquals(
            "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\"," +
                "\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\"," +
                "\"duration_seconds\",\"rpe\"\r\n",
            WorkoutCsvFormatter.header(),
        )
    }

    @Test
    fun `timestamps use the pinned English format regardless of device locale`() {
        val fields = WorkoutCsvFormatter.row(row(), utc).split(",")
        // "22 Dec 2025, 08:00" — a German-locale default would render "22 Dez 2025".
        assertEquals("\"22 Dec 2025", fields[1])
        assertTrue(fields[2].startsWith(" 08:00\""))
    }

    @Test
    fun `decimals use a dot on a comma-decimal device`() {
        val line = WorkoutCsvFormatter.row(row(weightKg = 102.5, rpe = 8.5), utc)
        assertTrue("expected a dot decimal, got: $line", line.contains("\"102.5\""))
        assertTrue(line.contains("\"8.5\""))
    }

    @Test
    fun `whole weights drop their trailing zeros`() {
        assertTrue(WorkoutCsvFormatter.row(row(weightKg = 100.0), utc).contains("\"100\""))
    }

    @Test
    fun `distance is written in kilometres, converted from metres`() {
        val line = WorkoutCsvFormatter.row(row(weightKg = null, reps = null, distanceMeters = 5250.0), utc)
        assertTrue(line, line.contains("\"5.25\""))
    }

    @Test
    fun `set types are lowercased`() {
        assertTrue(WorkoutCsvFormatter.row(row(setType = SetType.WARMUP), utc).contains("\"warmup\""))
        assertTrue(WorkoutCsvFormatter.row(row(setType = SetType.DROPSET), utc).contains("\"dropset\""))
    }

    @Test
    fun `absent values are empty cells, never zero`() {
        val line = WorkoutCsvFormatter.row(
            row(weightKg = null, reps = null, rpe = null, supersetGroup = null, exerciseNotes = null),
            utc,
        )
        // The five trailing metric columns are all absent here, and none may render as 0.
        // set_index legitimately IS 0, which is why this checks the cells rather than the line.
        assertTrue(
            "nulls must be empty cells: $line",
            line.endsWith("\"normal\",\"\",\"\",\"\",\"\",\"\"" + CsvWriter.LINE_SEPARATOR),
        )
    }

    @Test
    fun `a note containing a comma stays one field`() {
        val line = WorkoutCsvFormatter.row(row(workoutNotes = "felt strong, back tight"), utc)
        assertTrue(line, line.contains("\"felt strong, back tight\""))
    }

    @Test
    fun `a note containing a quote has it doubled`() {
        val line = WorkoutCsvFormatter.row(row(exerciseNotes = "used the 45\" bar"), utc)
        assertTrue(line, line.contains("\"used the 45\"\" bar\""))
    }

    @Test
    fun `a workout still in progress has an empty end time`() {
        val fields = WorkoutCsvFormatter.row(row(endedAt = null), utc).split(",")
        assertEquals("\"\"", fields[3])
    }
}
