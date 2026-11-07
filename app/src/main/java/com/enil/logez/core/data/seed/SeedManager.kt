package com.enil.logez.core.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * PHASE2_PLAN.md §7.7/§7.8 — runtime JSON seeding, version-gated so first install and every
 * later library update share one code path. New IDs insert; existing seed IDs get a
 * correction-safe field update (name/muscles/equipment/instructions/eligibility/isDeleted) —
 * `exercise_type` is never touched by an update, and custom exercises (`is_custom = 1`) are
 * never touched at all (enforced by [ExerciseDao.updateSeedFields]'s own WHERE clause).
 *
 * A seed bump also *retires* rows: any prior seed id absent from the current file is soft
 * deleted, never hard deleted (§7.9). This is how the placeholder 18-exercise proof-of-pipeline
 * set at seedVersion 1 was superseded by the frozen 400-exercise library at seedVersion 2 without
 * orphaning workouts already logged against the old ids — they keep resolving by id, they just
 * stop appearing in Browse/Create.
 */
@Singleton
class SeedManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val dataStore: DataStore<Preferences>,
    private val transactionRunner: TransactionRunner,
    private val logger: AppLogger = AppLogger.NoOp,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lastAppliedSeedVersionKey = intPreferencesKey("lastAppliedSeedVersion")

    /** Recorded in a backup's manifest, for diagnostics. */
    suspend fun lastAppliedSeedVersion(): Int = dataStore.data.first()[lastAppliedSeedVersionKey] ?: 0

    /**
     * Forgets which seed version has been applied, so the next [seedIfNeeded] runs in full.
     *
     * A restore needs this. The seed version lives in the same preferences store the backup
     * replaces, and a backup taken at an older version would otherwise leave the device convinced
     * it was already up to date — so the seed pass would return early forever and the user would
     * silently keep the older exercise library. Re-running it is safe: existing rows are left
     * alone, and a row the user has edited is exempt from seed updates entirely.
     */
    suspend fun resetSeedVersion() {
        dataStore.edit { it.remove(lastAppliedSeedVersionKey) }
    }

    suspend fun seedIfNeeded() {
        try {
            val file = readSeedFile()
            val lastApplied = dataStore.data.first()[lastAppliedSeedVersionKey] ?: 0
            if (file.seedVersion <= lastApplied) return

            val now = System.currentTimeMillis()
            val entities = file.exercises.map { it.toEntity(now) }

            // One transaction, not one per row: on an existing install nearly every row takes the
            // UPDATE branch, and 400 autocommitted updates take several seconds on slow storage --
            // long enough to be visible as a hang after a restore, which re-runs this.
            transactionRunner.runInTransaction {
                val insertResults = exerciseDao.insertIgnore(entities)

                entities.forEachIndexed { index, entity ->
                    if (insertResults[index] == -1L) {
                        exerciseDao.updateSeedFields(
                            id = entity.id,
                            name = entity.name,
                            primaryMuscleGroup = entity.primaryMuscleGroup,
                            secondaryMuscleGroups = entity.secondaryMuscleGroups,
                            equipment = entity.equipment,
                            instructions = entity.instructions,
                            isBodyweightVolumeEligible = entity.isBodyweightVolumeEligible,
                            isDeleted = false,
                            updatedAt = now,
                        )
                    }
                }

                exerciseDao.pruneRetiredSeeds(entities.map { it.id }, now)
            }

            dataStore.edit { it[lastAppliedSeedVersionKey] = file.seedVersion }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A malformed seed file or a transient Room/DataStore failure must not crash-loop the
            // app on every launch (lastAppliedSeedVersionKey only advances on success, so a bad
            // seed would otherwise fail identically forever). Degrade to "stays on whatever seed
            // version was last successfully applied" instead.
            logger.e(TAG, "Exercise seeding failed; continuing with the previously seeded library", e)
        }
    }

    private companion object {
        private const val TAG = "SeedManager"
    }

    private fun readSeedFile(): ExerciseSeedFile =
        context.assets.open("seed/exercises_seed.json").use { stream ->
            json.decodeFromString(ExerciseSeedFile.serializer(), stream.readBytes().decodeToString())
        }

    private fun ExerciseSeedDto.toEntity(now: Long) = ExerciseEntity(
        id = id,
        name = name,
        exerciseType = ExerciseType.valueOf(exerciseType),
        primaryMuscleGroup = MuscleGroup.valueOf(primaryMuscleGroup),
        secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.valueOf(it) },
        equipment = Equipment.valueOf(equipment),
        instructions = instructions.joinToString("\n"),
        mediaPath = null,
        isCustom = false,
        isBodyweightVolumeEligible = isBodyweightVolumeEligible,
        isDeleted = false,
        createdAt = now,
        updatedAt = now,
    )
}
