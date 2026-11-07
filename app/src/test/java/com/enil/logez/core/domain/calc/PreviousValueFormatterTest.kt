package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviousValueFormatterTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun set(
        weightKg: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
        rpe: Double? = null,
    ) = StatSet(
        setId = "s1", workoutId = "w1", workoutStartedAt = 0L, orderIndex = 0, setType = SetType.NORMAL,
        weightKg = weightKg, reps = reps, durationSeconds = durationSeconds, distanceMeters = distanceMeters,
        customMetric = null, isCompleted = true, rpe = rpe,
    )

    // --- WEIGHT_REPS (and its bodyweight siblings, which share the same branch) ---

    @Test
    fun `WEIGHT_REPS formats as value unit x reps, in kg`() {
        val result = PreviousValueFormatter.format(
            set(weightKg = 100.0, reps = 8), ExerciseType.WEIGHT_REPS, WeightUnit.KG, DistanceUnit.KM,
        )
        assertEquals("100 kg x 8", result)
    }

    @Test
    fun `WEIGHT_REPS converts to lb and rounds to one decimal`() {
        val result = PreviousValueFormatter.format(
            set(weightKg = 80.0, reps = 8), ExerciseType.WEIGHT_REPS, WeightUnit.LB, DistanceUnit.KM,
        )
        assertEquals("176.4 lb x 8", result)
    }

    @Test
    fun `WEIGHT_REPS with a null weight or null reps renders the em dash placeholder`() {
        assertEquals("—", PreviousValueFormatter.format(set(weightKg = null, reps = 8), ExerciseType.WEIGHT_REPS, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("—", PreviousValueFormatter.format(set(weightKg = 100.0, reps = null), ExerciseType.WEIGHT_REPS, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `BODYWEIGHT_WEIGHTED and BODYWEIGHT_ASSISTED share WEIGHT_REPS's value-unit-x-reps formatting`() {
        val s = set(weightKg = 20.0, reps = 6)
        val expected = "20 kg x 6"
        assertEquals(expected, PreviousValueFormatter.format(s, ExerciseType.BODYWEIGHT_WEIGHTED, WeightUnit.KG, DistanceUnit.KM))
        assertEquals(expected, PreviousValueFormatter.format(s, ExerciseType.BODYWEIGHT_ASSISTED, WeightUnit.KG, DistanceUnit.KM))
    }

    // --- REPS_ONLY ---

    @Test
    fun `REPS_ONLY formats as N reps`() {
        val result = PreviousValueFormatter.format(set(reps = 12), ExerciseType.REPS_ONLY, WeightUnit.KG, DistanceUnit.KM)
        assertEquals("12 reps", result)
    }

    @Test
    fun `REPS_ONLY with no reps logged renders the em dash placeholder`() {
        assertEquals("—", PreviousValueFormatter.format(set(reps = null), ExerciseType.REPS_ONLY, WeightUnit.KG, DistanceUnit.KM))
    }

    // --- DURATION (and its FLOORS_DURATION/STEPS_DURATION siblings, which share the same branch) ---

    @Test
    fun `DURATION formats seconds as m colon ss`() {
        val result = PreviousValueFormatter.format(set(durationSeconds = 125), ExerciseType.DURATION, WeightUnit.KG, DistanceUnit.KM)
        assertEquals("2:05", result)
    }

    @Test
    fun `FLOORS_DURATION and STEPS_DURATION share DURATION's mm colon ss formatting`() {
        val s = set(durationSeconds = 90)
        assertEquals("1:30", PreviousValueFormatter.format(s, ExerciseType.FLOORS_DURATION, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("1:30", PreviousValueFormatter.format(s, ExerciseType.STEPS_DURATION, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `DURATION with no duration logged renders the em dash placeholder`() {
        assertEquals("—", PreviousValueFormatter.format(set(durationSeconds = null), ExerciseType.DURATION, WeightUnit.KG, DistanceUnit.KM))
    }

    // --- WEIGHT_DURATION ---

    @Test
    fun `WEIGHT_DURATION formats as weight unit x mm colon ss, in kg and lb`() {
        val s = set(weightKg = 20.0, durationSeconds = 45)
        assertEquals("20 kg x 0:45", PreviousValueFormatter.format(s, ExerciseType.WEIGHT_DURATION, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("44.1 lb x 0:45", PreviousValueFormatter.format(s, ExerciseType.WEIGHT_DURATION, WeightUnit.LB, DistanceUnit.KM))
    }

    @Test
    fun `WEIGHT_DURATION with a null weight or null duration renders the em dash placeholder`() {
        assertEquals("—", PreviousValueFormatter.format(set(weightKg = null, durationSeconds = 45), ExerciseType.WEIGHT_DURATION, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("—", PreviousValueFormatter.format(set(weightKg = 20.0, durationSeconds = null), ExerciseType.WEIGHT_DURATION, WeightUnit.KG, DistanceUnit.KM))
    }

    // --- DISTANCE_DURATION (cardio: run/walk) ---

    @Test
    fun `DISTANCE_DURATION formats as distance unit slash mm colon ss, in km`() {
        val s = set(distanceMeters = 5000.0, durationSeconds = 1500)
        assertEquals("5 km / 25:00", PreviousValueFormatter.format(s, ExerciseType.DISTANCE_DURATION, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `DISTANCE_DURATION converts to miles and rounds to one decimal`() {
        val s = set(distanceMeters = 500.0, durationSeconds = 150)
        assertEquals("0.3 mi / 2:30", PreviousValueFormatter.format(s, ExerciseType.DISTANCE_DURATION, WeightUnit.KG, DistanceUnit.MILES))
    }

    @Test
    fun `DISTANCE_DURATION with a null distance or null duration renders the em dash placeholder`() {
        assertEquals("—", PreviousValueFormatter.format(set(distanceMeters = null, durationSeconds = 1500), ExerciseType.DISTANCE_DURATION, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("—", PreviousValueFormatter.format(set(distanceMeters = 5000.0, durationSeconds = null), ExerciseType.DISTANCE_DURATION, WeightUnit.KG, DistanceUnit.KM))
    }

    // --- WEIGHT_DISTANCE (e.g. a weighted carry) ---

    @Test
    fun `WEIGHT_DISTANCE formats as weight unit x distance unit`() {
        val s = set(weightKg = 40.0, distanceMeters = 2500.0)
        assertEquals("40 kg x 2.5 km", PreviousValueFormatter.format(s, ExerciseType.WEIGHT_DISTANCE, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `WEIGHT_DISTANCE with a null weight or null distance renders the em dash placeholder`() {
        assertEquals("—", PreviousValueFormatter.format(set(weightKg = null, distanceMeters = 20.0), ExerciseType.WEIGHT_DISTANCE, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("—", PreviousValueFormatter.format(set(weightKg = 40.0, distanceMeters = null), ExerciseType.WEIGHT_DISTANCE, WeightUnit.KG, DistanceUnit.KM))
    }

    // --- formatRpeLine ---

    @Test
    fun `formatRpeLine renders RPE with its label when present`() {
        assertEquals("RPE 8.5", PreviousValueFormatter.formatRpeLine(set(rpe = 8.5)))
    }

    @Test
    fun `formatRpeLine is null when the previous set has no RPE`() {
        assertNull(PreviousValueFormatter.formatRpeLine(set(rpe = null)))
    }

    @Test
    fun `formatRpeLine renders a whole-number RPE without a trailing decimal`() {
        assertEquals("RPE 9", PreviousValueFormatter.formatRpeLine(set(rpe = 9.0)))
    }

    // --- Locale.ROOT guard (comma-decimal regression) ---

    @Test
    fun `non-integer values use a dot decimal separator regardless of the device's default locale`() {
        Locale.setDefault(Locale.GERMANY) // comma-decimal locale
        val result = PreviousValueFormatter.format(
            set(weightKg = 80.0, reps = 8), ExerciseType.WEIGHT_REPS, WeightUnit.LB, DistanceUnit.KM,
        )
        assertEquals("176.4 lb x 8", result)
    }
}
