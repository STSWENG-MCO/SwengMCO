package com.enil.logez.core.common

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead

/** Shared Title Case formatters for enum values shown in the UI (library filters, editor form, detail screens). */

fun muscleGroupLabel(group: MuscleGroup): String = when (group) {
    MuscleGroup.ABDOMINALS -> "Abdominals"
    MuscleGroup.SHOULDERS -> "Shoulders"
    MuscleGroup.BICEPS -> "Biceps"
    MuscleGroup.TRICEPS -> "Triceps"
    MuscleGroup.FOREARMS -> "Forearms"
    MuscleGroup.QUADRICEPS -> "Quadriceps"
    MuscleGroup.HAMSTRINGS -> "Hamstrings"
    MuscleGroup.CALVES -> "Calves"
    MuscleGroup.GLUTES -> "Glutes"
    MuscleGroup.ABDUCTORS -> "Abductors"
    MuscleGroup.ADDUCTORS -> "Adductors"
    MuscleGroup.LATS -> "Lats"
    MuscleGroup.UPPER_BACK -> "Upper Back"
    MuscleGroup.TRAPS -> "Traps"
    MuscleGroup.LOWER_BACK -> "Lower Back"
    MuscleGroup.CHEST -> "Chest"
    MuscleGroup.CARDIO -> "Cardio"
    MuscleGroup.NECK -> "Neck"
    MuscleGroup.FULL_BODY -> "Full Body"
    MuscleGroup.OTHER -> "Other"
}

fun muscleHeadLabel(head: MuscleHead): String = when (head) {
    MuscleHead.ANTERIOR_DELTOID -> "Anterior (Front) Delt"
    MuscleHead.LATERAL_DELTOID -> "Lateral (Side) Delt"
    MuscleHead.POSTERIOR_DELTOID -> "Posterior (Rear) Delt"
    MuscleHead.UPPER_CHEST -> "Upper Chest"
    MuscleHead.LOWER_CHEST -> "Lower Chest"
    MuscleHead.TRICEPS_LATERAL_HEAD -> "Lateral Head"
    MuscleHead.TRICEPS_LONG_HEAD -> "Long Head"
    MuscleHead.GASTROCNEMIUS -> "Gastrocnemius"
    MuscleHead.SOLEUS -> "Soleus"
    MuscleHead.UPPER_LATS -> "Upper Lats"
    MuscleHead.MID_LATS -> "Mid Lats"
    MuscleHead.LOWER_LATS -> "Lower Lats"
    MuscleHead.LATERAL_HAMSTRING -> "Lateral (Biceps Femoris)"
    MuscleHead.MEDIAL_HAMSTRING -> "Medial (Semitendinosus/-membranosus)"
}

fun equipmentLabel(equipment: Equipment): String = when (equipment) {
    Equipment.NONE -> "None"
    Equipment.BARBELL -> "Barbell"
    Equipment.DUMBBELL -> "Dumbbell"
    Equipment.KETTLEBELL -> "Kettlebell"
    Equipment.MACHINE -> "Machine"
    Equipment.PLATE -> "Plate"
    Equipment.RESISTANCE_BAND -> "Resistance Band"
    Equipment.SUSPENSION -> "Suspension"
    Equipment.OTHER -> "Other"
}

fun exerciseTypeLabel(type: ExerciseType): String = when (type) {
    ExerciseType.WEIGHT_REPS -> "Weight & Reps"
    ExerciseType.REPS_ONLY -> "Bodyweight Reps"
    ExerciseType.BODYWEIGHT_WEIGHTED -> "Weighted Bodyweight"
    ExerciseType.BODYWEIGHT_ASSISTED -> "Assisted Bodyweight"
    ExerciseType.DURATION -> "Duration"
    ExerciseType.WEIGHT_DURATION -> "Duration & Weight"
    ExerciseType.DISTANCE_DURATION -> "Distance & Duration"
    ExerciseType.WEIGHT_DISTANCE -> "Weight & Distance"
    ExerciseType.FLOORS_DURATION -> "Floors"
    ExerciseType.STEPS_DURATION -> "Steps"
}
