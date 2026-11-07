package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.SetType

/**
 * Stable callback container for [WorkoutExerciseCard] and its children ([SetTable], [CircuitEntry]).
 *
 * Replaces the previous `viewModel: WorkoutLoggerViewModel` parameter — every composable that
 * previously held a reference to the full ViewModel now receives only the callbacks it actually
 * needs. Each lambda captures the exercise ID at the call site, so the callbacks object itself
 * is stable across recompositions (no per-card state leaks into the composable signature).
 *
 * Construction lives in [WorkoutLoggerScreen] where the ViewModel is available; the card
 * composables never import or reference the ViewModel type.
 */
data class WorkoutCallbacks(
    // --- Exercise-level operations (used by WorkoutExerciseCard header/menu) ---
    val onStartSupersetSelection: (exerciseId: String) -> Unit,
    val onConfirmSupersetTarget: (exerciseId: String) -> Unit,
    val onRemoveFromSuperset: (exerciseId: String) -> Unit,
    val onRemoveExercise: (exerciseId: String) -> Unit,
    val onUpdateNotes: (exerciseId: String, text: String) -> Unit,
    val onAddSet: (exerciseId: String) -> Unit,
    /** M18 §5.1.6: inserts the Warmup Method ladder above set 1 (regular workouts only). */
    val onAddWarmupSets: (exerciseId: String) -> Unit,

    // --- Per-set operations (used by SetTable / CircuitEntry → SetRow) ---
    val onUpdateSetType: (exerciseId: String, setId: String, type: SetType) -> Unit,
    val onRemoveSet: (exerciseId: String, setId: String) -> Unit,
    val onUpdateWeight: (exerciseId: String, setId: String, kg: Double?) -> Unit,
    val onUpdateReps: (exerciseId: String, setId: String, reps: Int?) -> Unit,
    val onUpdateDuration: (exerciseId: String, setId: String, seconds: Int?) -> Unit,
    val onUpdateDistance: (exerciseId: String, setId: String, meters: Double?) -> Unit,
    val onUpdateCustomMetric: (exerciseId: String, setId: String, value: Double?) -> Unit,
    val onToggleCheck: (exerciseId: String, setId: String) -> Boolean,
    val onUpdateRpe: (exerciseId: String, setId: String, rpe: Double?) -> Unit,
    val onStartInlineTimer: (exerciseId: String, setId: String) -> Unit,
    val onStopInlineTimer: (exerciseId: String, setId: String) -> Unit,
    /** M20d: opens the screen-hoisted plate calculator sheet targeting this exact set. */
    val onOpenPlateCalculator: (exerciseId: String, setId: String, currentWeightKg: Double?) -> Unit,
)
