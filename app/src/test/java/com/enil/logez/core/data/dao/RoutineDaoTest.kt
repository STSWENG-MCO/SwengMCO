package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteConstraintException

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutineDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.routineDao()
    private val exerciseDao get() = database.exerciseDao()

    private suspend fun anExercise(id: String = "ex-1") {
        exerciseDao.insertIgnore(
            listOf(
                ExerciseEntity(
                    id = id,
                    name = "Bench Press (Barbell)",
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST,
                    secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL,
                    instructions = "x",
                    mediaPath = null,
                    isCustom = false,
                    isBodyweightVolumeEligible = false,
                    isDeleted = false,
                    createdAt = 0,
                    updatedAt = 0,
                ),
            ),
        )
    }

    @Test
    fun `deleting a folder SETs NULL on its routines, not cascade`() = runTest {
        val folder = RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)
        dao.upsertFolder(folder)
        dao.upsertRoutine(RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))

        dao.deleteFolder(folder)

        val routine = dao.getRoutineById("r1")
        assertEquals("r1", routine?.id) // routine survives
        assertNull(routine?.folderId) // FK SET_NULL, not deleted
    }

    @Test
    fun `deleting a routine cascades to its exercises and sets`() = runTest {
        anExercise()
        dao.insertFullRoutine(
            routine = RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            exercises = listOf(
                RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                RoutineSetEntity(id = "rs1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 100.0, targetReps = 5, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null),
            ),
        )

        dao.deleteRoutineById("r1")

        assertTrue(dao.getExercisesForRoutine("r1").isEmpty())
        assertEquals(0, dao.getSetsForRoutineExercise("re1").size)
    }

    @Test
    fun `deleting a referenced exercise is rejected by the schema (FK RESTRICT)`() = runTest {
        anExercise()
        dao.insertFullRoutine(
            routine = RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            exercises = listOf(
                RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = emptyList(),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            rawDb.execSQL("DELETE FROM exercises WHERE id = 'ex-1'")
        }
    }

    @Test
    fun `folders and routines observe in orderIndex order`() = runTest {
        dao.upsertFolder(RoutineFolderEntity(id = "f2", name = "Second", orderIndex = 1, createdAt = 0, updatedAt = 0))
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "First", orderIndex = 0, createdAt = 0, updatedAt = 0))

        val folders = dao.observeFolders().first()
        assertEquals(listOf("f1", "f2"), folders.map { it.id })
    }

    @Test
    fun `observeRoutinesInFolder with a null folderId matches root routines, not zero rows`() = runTest {
        // Regression test: the original query used "=" against a nullable bind param, which SQLite
        // never matches against NULL — root routines were silently invisible until fixed to "IS".
        dao.upsertRoutine(RoutineEntity(id = "root1", folderId = null, name = "Root Routine", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertRoutine(RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))

        val rootRoutines = dao.observeRoutinesInFolder(null).first()
        assertEquals(listOf("root1"), rootRoutines.map { it.id })
        val folderRoutines = dao.getRoutinesInFolderOnce("f1")
        assertEquals(listOf("r1"), folderRoutines.map { it.id })
    }

    @Test
    fun `createFolderAtTop inserts at index 0 and shifts existing folders down`() = runTest {
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "Existing", orderIndex = 0, createdAt = 0, updatedAt = 0))

        dao.createFolderAtTop(RoutineFolderEntity(id = "f2", name = "New", orderIndex = 0, createdAt = 0, updatedAt = 0))

        val folders = dao.getAllFoldersOnce()
        assertEquals(listOf("f2", "f1"), folders.map { it.id })
        assertEquals(0, folders.first { it.id == "f2" }.orderIndex)
        assertEquals(1, folders.first { it.id == "f1" }.orderIndex)
    }

    @Test
    fun `reorderFolders persists the given order`() = runTest {
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "A", orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertFolder(RoutineFolderEntity(id = "f2", name = "B", orderIndex = 1, createdAt = 0, updatedAt = 0))

        dao.reorderFolders(listOf("f2", "f1"))

        val folders = dao.getAllFoldersOnce()
        assertEquals(listOf("f2", "f1"), folders.map { it.id })
    }

    @Test
    fun `createRoutineAtTop inserts at index 0 within its own bucket only`() = runTest {
        anExercise()
        dao.upsertRoutine(RoutineEntity(id = "root1", folderId = null, name = "Root Routine", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertRoutine(RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))

        dao.createRoutineAtTop(
            routine = RoutineEntity(id = "r2", folderId = "f1", name = "Pull Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            exercises = emptyList(),
            sets = emptyList(),
        )

        assertEquals(listOf("r2", "r1"), dao.getRoutinesInFolderOnce("f1").map { it.id })
        assertEquals(0, dao.getRoutineById("root1")!!.orderIndex) // root bucket untouched by a folder-bucket insert
    }

    @Test
    fun `updateRoutineStructure replaces exercises and sets without moving the routine's position`() = runTest {
        anExercise("ex-1")
        anExercise("ex-2")
        dao.insertFullRoutine(
            routine = RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 5, createdAt = 0, updatedAt = 0),
            exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 100.0, targetReps = 5, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null)),
        )

        dao.updateRoutineStructure(
            routine = RoutineEntity(id = "r1", folderId = null, name = "Push Day (renamed)", notes = null, orderIndex = 5, createdAt = 0, updatedAt = 1),
            exercises = listOf(RoutineExerciseEntity(id = "re2", routineId = "r1", exerciseId = "ex-2", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(RoutineSetEntity(id = "s2", routineExerciseId = "re2", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 50.0, targetReps = 10, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null)),
        )

        val routine = dao.getRoutineById("r1")!!
        assertEquals("Push Day (renamed)", routine.name)
        assertEquals(5, routine.orderIndex) // unchanged
        val exercises = dao.getExercisesForRoutine("r1")
        assertEquals(listOf("re2"), exercises.map { it.id }) // old exercise gone
        assertEquals(0, dao.getSetsForRoutineExercise("re1").size) // old sets cascaded away
        assertEquals(1, dao.getSetsForRoutineExercise("re2").size)
    }

    @Test
    fun `moveRoutineToFolder updates folder and re-homes to the top of the new bucket`() = runTest {
        dao.upsertFolder(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertRoutine(RoutineEntity(id = "existing", folderId = "f1", name = "Existing", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertRoutine(RoutineEntity(id = "root1", folderId = null, name = "Root Routine", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))

        dao.moveRoutineToFolder("root1", "f1", updatedAt = 999)

        val moved = dao.getRoutineById("root1")!!
        assertEquals("f1", moved.folderId)
        assertEquals(0, moved.orderIndex)
        assertEquals(999, moved.updatedAt)
        assertEquals(1, dao.getRoutineById("existing")!!.orderIndex) // shifted down
    }

    @Test
    fun `observeRoutineExercisePreviews joins exercise names ordered by routine then position`() = runTest {
        anExercise("ex-1")
        exerciseDao.insertIgnore(
            listOf(
                ExerciseEntity(
                    id = "ex-2", name = "Incline Press", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
                    secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
                    isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
                ),
            ),
        )
        dao.upsertRoutine(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.insertRoutineExercises(
            listOf(
                RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                RoutineExerciseEntity(id = "re2", routineId = "r1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
        )

        val rows = dao.observeRoutineExercisePreviews().first()
        assertEquals(listOf("Bench Press (Barbell)", "Incline Press"), rows.map { it.exerciseName })
    }
}
