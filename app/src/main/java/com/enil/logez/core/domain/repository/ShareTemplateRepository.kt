package com.enil.logez.core.domain.repository

import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.WorkoutKind
import kotlinx.coroutines.flow.Flow

/** The Share editor's remembered choice, kept separately for each kind of workout. */
interface ShareTemplateRepository {
    /** Never throws on bad stored data: anything missing or unknown falls back to the default. */
    fun choice(kind: WorkoutKind): Flow<ShareTemplateChoice>

    suspend fun save(
        kind: WorkoutKind,
        choice: ShareTemplateChoice,
    )

    suspend fun reset(kind: WorkoutKind)
}
