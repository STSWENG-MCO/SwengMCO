package com.enil.logez.feature.exercises

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PHASE2_PLAN.md §10.6 M2 row — search, equipment/muscle filters, and the recently-used sort.
 * Tests the pure function directly (no ViewModel/repository fakes needed) since the logic under
 * test has zero platform dependency — the ViewModel just wires this to Flows.
 */
class ExerciseLibraryFilterSortTest {
    private fun exercise(
        id: String,
        name: String,
        primary: MuscleGroup = MuscleGroup.CHEST,
        secondary: List<MuscleGroup> = emptyList(),
        equipment: Equipment = Equipment.BARBELL,
        isCustom: Boolean = false,
    ) = Exercise(
        id = id,
        name = name,
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = primary,
        secondaryMuscleGroups = secondary,
        equipment = equipment,
        instructions = "",
        mediaPath = null,
        isCustom = isCustom,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `search matches name case-insensitively as a substring`() {
        val exercises = listOf(
            exercise("a", "Bench Press (Barbell)"),
            exercise("b", "Squat (Barbell)"),
        )
        val result = applyFiltersAndSort(exercises, LibraryFilters(searchQuery = "bench"), emptyMap())
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `equipment filter matches exact equipment only`() {
        val exercises = listOf(
            exercise("a", "Bench Press (Barbell)", equipment = Equipment.BARBELL),
            exercise("b", "Bench Press (Dumbbell)", equipment = Equipment.DUMBBELL),
        )
        val result = applyFiltersAndSort(exercises, LibraryFilters(equipmentFilter = Equipment.DUMBBELL), emptyMap())
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `muscle filter matches primary OR secondary membership`() {
        val exercises = listOf(
            exercise("a", "Bench Press", primary = MuscleGroup.CHEST, secondary = listOf(MuscleGroup.TRICEPS)),
            exercise("b", "Triceps Pushdown", primary = MuscleGroup.TRICEPS, secondary = emptyList()),
            exercise("c", "Squat", primary = MuscleGroup.QUADRICEPS, secondary = emptyList()),
        )
        val result = applyFiltersAndSort(exercises, LibraryFilters(muscleFilter = MuscleGroup.TRICEPS), emptyMap())
        assertEquals(setOf("a", "b"), result.map { it.id }.toSet())
    }

    @Test
    fun `sort tiers recently-used first by most recent, then never-used custom, then the rest alphabetically`() {
        val exercises = listOf(
            exercise("seed-z", "Zebra Curl", isCustom = false),
            exercise("seed-a", "Ab Crunch", isCustom = false),
            exercise("custom-b", "My Custom B", isCustom = true),
            exercise("custom-a", "My Custom A", isCustom = true),
            exercise("used-old", "Used Long Ago", isCustom = false),
            exercise("used-new", "Used Recently", isCustom = false),
        )
        val recentUsage = mapOf("used-old" to 1_000L, "used-new" to 5_000L)

        val result = applyFiltersAndSort(exercises, LibraryFilters(), recentUsage)

        assertEquals(
            listOf("used-new", "used-old", "custom-a", "custom-b", "seed-a", "seed-z"),
            result.map { it.id },
        )
    }

    @Test
    fun `combined search and filters narrow correctly`() {
        val exercises = listOf(
            exercise("a", "Bench Press (Barbell)", primary = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
            exercise("b", "Bench Press (Dumbbell)", primary = MuscleGroup.CHEST, equipment = Equipment.DUMBBELL),
            exercise("c", "Squat (Barbell)", primary = MuscleGroup.QUADRICEPS, equipment = Equipment.BARBELL),
        )
        val result = applyFiltersAndSort(
            exercises,
            LibraryFilters(searchQuery = "bench", equipmentFilter = Equipment.BARBELL, muscleFilter = MuscleGroup.CHEST),
            emptyMap(),
        )
        assertEquals(listOf("a"), result.map { it.id })
    }
}
