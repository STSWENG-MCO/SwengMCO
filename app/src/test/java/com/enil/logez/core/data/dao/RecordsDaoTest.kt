package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.recordsDao()

    private suspend fun fixtures() {
        database.exerciseDao().insertIgnore(
            listOf(
                ExerciseEntity(
                    id = "ex-1", name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL, instructions = "x", mediaPath = null, isCustom = false,
                    isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
                ),
            ),
        )
        database.workoutDao().upsertWorkout(
            WorkoutEntity(id = "w1", routineId = null, title = "Push", notes = null, status = WorkoutStatus.COMPLETED, startedAt = 0, endedAt = 100, durationSeconds = 100, createdAt = 0, updatedAt = 0),
        )
    }

    @Test
    fun `rebuildFor is a delete-then-insert, never an incremental patch`() = runTest {
        fixtures()
        dao.rebuildFor("ex-1", listOf(PersonalRecordEntity(id = "pr1", exerciseId = "ex-1", workoutId = "w1", workoutSetId = null, prType = PrType.HEAVIEST_WEIGHT, value = 100.0, achievedAt = 0)))
        assertEquals(1, dao.getForWorkout("w1").size)

        // A second rebuild with different records must fully replace, not merge, the first set.
        dao.rebuildFor(
            "ex-1",
            listOf(
                PersonalRecordEntity(id = "pr2", exerciseId = "ex-1", workoutId = "w1", workoutSetId = null, prType = PrType.HEAVIEST_WEIGHT, value = 110.0, achievedAt = 0),
                PersonalRecordEntity(id = "pr3", exerciseId = "ex-1", workoutId = "w1", workoutSetId = null, prType = PrType.BEST_1RM, value = 120.0, achievedAt = 0),
            ),
        )

        val records = dao.getForWorkout("w1")
        assertEquals(2, records.size)
        assertEquals(110.0, records.first { it.prType == PrType.HEAVIEST_WEIGHT }.value, 0.0)
    }
}
