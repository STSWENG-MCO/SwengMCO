package com.enil.logez.feature.workout.finish.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.core.domain.model.ShareTemplate
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.ShareTemplateRegistry
import com.enil.logez.core.domain.model.WorkoutKind
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

internal class ShareTemplateState(
    val choice: ShareTemplateChoice,
    val select: (ShareTemplate) -> Unit,
)

@Composable
internal fun rememberShareTemplateState(kind: WorkoutKind): ShareTemplateState {
    val context = LocalContext.current
    val repository =
        remember(context) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, WorkoutShareEntryPoint::class.java)
                .shareTemplateRepository()
        }
    val flow = remember(repository, kind) { repository.choice(kind) }
    val choice by flow.collectAsStateWithLifecycle(initialValue = ShareTemplateRegistry.defaultChoice(kind))
    val scope = rememberCoroutineScope()
    return ShareTemplateState(choice) { template ->
        scope.launch { repository.save(kind, choice.copy(template = template)) }
    }
}
