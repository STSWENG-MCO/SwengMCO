package com.enil.logez.core.data.seed

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE2_PLAN.md §10.5 — runs against the raw seed asset, a plain JVM test with no Room/Android
 * dependency. Validates the frozen 400-exercise library (§7.9, Owner-approved 2026-08-24, P-018;
 * see docs/seed-skeleton-400.json and docs/SEED_SKELETON_400_REVIEW.md for the source list and
 * its distribution rationale). Instruction text is a deliberate follow-up sub-step — every entry
 * ships `instructions: []` for now — so this file does NOT assert non-empty instructions; that
 * assertion returns once the instruction-writing pass lands.
 */
class SeedLibraryValidationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val expectedExerciseCount = 400

    /**
     * The 4 base bodyweight movements plus their weighted/assisted variants, exactly as
     * docs/SEED_SKELETON_400_REVIEW.md's 🔵 markers define (Handstand Push Up has no
     * weighted/assisted variant in the 400, so it contributes only its own row).
     */
    private val expectedEligibleNames = setOf(
        "Pull Up", "Pull Up (Weighted)", "Pull Up (Assisted)", "Pull Up (Band)",
        "Chin Up", "Chin Up (Weighted)", "Chin Up (Assisted)",
        "Dips", "Dips (Weighted)", "Dips (Assisted)",
        "Handstand Push Up",
    )

    private fun loadSeedFile(): ExerciseSeedFile {
        val file = File("src/main/assets/seed/exercises_seed.json")
        assertTrue("seed asset not found at ${file.absolutePath}", file.exists())
        return json.decodeFromString(ExerciseSeedFile.serializer(), file.readText())
    }

    @Test
    fun `file parses, has the frozen 400-entry count, and every entry has well-formed id and name`() {
        val seedFile = loadSeedFile()
        assertEquals(expectedExerciseCount, seedFile.exercises.size)
        seedFile.exercises.forEach { e ->
            assertTrue("blank id", e.id.isNotBlank())
            assertTrue("blank name for ${e.id}", e.name.isNotBlank())
        }
    }

    @Test
    fun `the walk and run ids the summary uses to label a GPS workout are the real seed rows`() {
        val byId = loadSeedFile().exercises.associateBy { it.id }
        assertEquals("Running (Outdoor)", byId[com.enil.logez.core.domain.model.GpsActivity.RUNNING_OUTDOOR_EXERCISE_ID]?.name)
        assertEquals("Walking (Outdoor)", byId[com.enil.logez.core.domain.model.GpsActivity.WALKING_OUTDOOR_EXERCISE_ID]?.name)
    }

    @Test
    fun `every enum field maps to a real domain enum value`() {
        loadSeedFile().exercises.forEach { e ->
            ExerciseType.valueOf(e.exerciseType) // throws IllegalArgumentException on a bad value
            MuscleGroup.valueOf(e.primaryMuscleGroup)
            e.secondaryMuscleGroups.forEach { MuscleGroup.valueOf(it) }
            Equipment.valueOf(e.equipment)
        }
    }

    @Test
    fun `ids and names are unique`() {
        val exercises = loadSeedFile().exercises
        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        assertEquals(exercises.size, exercises.map { it.name }.distinct().size)
    }

    @Test
    fun `isBodyweightVolumeEligible is set on exactly the intended rows`() {
        val actual = loadSeedFile().exercises.filter { it.isBodyweightVolumeEligible }.map { it.name }.toSet()
        assertEquals(expectedEligibleNames, actual)
    }

    @Test
    fun `every eligible row is REPS_ONLY or a weighted-assisted variant, never WEIGHT_REPS`() {
        loadSeedFile().exercises.filter { it.isBodyweightVolumeEligible }.forEach { e ->
            val type = ExerciseType.valueOf(e.exerciseType)
            assertTrue(
                "${e.id} is eligible but has type $type",
                type == ExerciseType.REPS_ONLY || type == ExerciseType.BODYWEIGHT_WEIGHTED || type == ExerciseType.BODYWEIGHT_ASSISTED,
            )
        }
    }
}
