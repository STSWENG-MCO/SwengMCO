package com.enil.logez.feature.routines

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.designsystem.CircuitChip
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.HeatmapGrid
import com.enil.logez.core.designsystem.DragHandle
import com.enil.logez.core.designsystem.SyncOptimisticList
import com.enil.logez.core.designsystem.Elevation
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.feature.activity.ActivityTrackingStartResult
import com.enil.logez.feature.activity.rememberRequestLocationForTracking
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.activity.startActivityTrackingService
import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import java.util.Locale
import java.time.format.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.enil.logez.core.common.PermissionDenial
import com.enil.logez.core.common.openAppDetailsSettings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult

/** What "Discard & start new" should start, once the in-progress conflict is resolved (§5.1.1 edge case). */
private sealed class PendingStart {
    object Empty : PendingStart()
    data class Routine(val routineId: String) : PendingStart()
    data class ActivityTracking(val exerciseId: String, val title: String) : PendingStart()
}

/** PHASE2_PLAN.md §5.1.1 — folders + routines home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTabScreen(
    onRoutineClick: (routineId: String) -> Unit,
    onCreateRoutine: (folderId: String?) -> Unit,
    onEditRoutine: (routineId: String) -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    viewModel: WorkoutTabViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val goalsUiState by goalsViewModel.uiState.collectAsStateWithLifecycle()
    val quickTrackExercises by viewModel.quickTrackExercises.collectAsStateWithLifecycle()
    val todaySteps by viewModel.todaySteps.collectAsStateWithLifecycle()
    val recentSteps by viewModel.recentSteps.collectAsStateWithLifecycle()
    RefreshOnResume(goalsViewModel::refresh)
    RefreshOnResume(viewModel::refreshSteps)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var collapsedFolders by rememberSaveable { mutableStateOf(setOf<String>()) }
    // Exposed as MutableState (not just `var x by remember {}`) so WorkoutTabDialogs below can
    // read and write the same instance — same delegate object either way, this just lets it
    // cross a composable-function boundary.
    val showCreateFolderState = remember { mutableStateOf(false) }
    var showCreateFolder by showCreateFolderState
    val renamingFolderState = remember { mutableStateOf<RoutineFolderEntity?>(null) }
    var renamingFolder by renamingFolderState
    val deletingFolderState = remember { mutableStateOf<RoutineFolderEntity?>(null) }
    var deletingFolder by deletingFolderState
    val deletingRoutineIdState = remember { mutableStateOf<String?>(null) }
    var deletingRoutineId by deletingRoutineIdState
    val movingRoutineIdState = remember { mutableStateOf<String?>(null) }
    var movingRoutineId by movingRoutineIdState
    val pendingStartState = remember { mutableStateOf<PendingStart?>(null) }
    var pendingStart by pendingStartState
    var inProgressWorkoutId by remember { mutableStateOf<String?>(null) }
    var pendingTrackExercise by remember { mutableStateOf<Exercise?>(null) }
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)

    fun startEmpty() = scope.launch {
        when (val result = viewModel.startEmptyWorkout()) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { pendingStart = PendingStart.Empty; inProgressWorkoutId = result.workoutId }
        }
    }

    fun startRoutine(routineId: String) = scope.launch {
        when (val result = viewModel.startRoutine(routineId)) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { pendingStart = PendingStart.Routine(routineId); inProgressWorkoutId = result.workoutId }
        }
    }

    val requestLocation = rememberRequestLocationForTracking(
        onGranted = {
            val exercise = pendingTrackExercise
            pendingTrackExercise = null
            if (exercise != null) {
                scope.launch {
                    when (val result = viewModel.startActivityTracking(exercise.id, exercise.name)) {
                        is ActivityTrackingStartResult.Started -> {
                            startActivityTrackingService(context)
                            onNavigateToActivityTracking()
                        }
                        is ActivityTrackingStartResult.AlreadyInProgress -> {
                            pendingStart = PendingStart.ActivityTracking(exercise.id, exercise.name)
                            inProgressWorkoutId = result.workoutId
                        }
                    }
                }
            }
        },
        onDenied = { denial ->
            pendingTrackExercise = null
            scope.launch {
                val message = resources.getString(
                    when (denial) {
                        PermissionDenial.Declined -> R.string.activity_tracking_location_denied
                        PermissionDenial.ApproximateOnly -> R.string.activity_tracking_location_approximate
                        PermissionDenial.Blocked -> R.string.activity_tracking_location_blocked
                    },
                )
                // Only offer Settings when it is the one place left to fix it.
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (denial == PermissionDenial.Declined) null else resources.getString(R.string.permission_open_settings),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) openAppDetailsSettings(context)
            }
        },
    )

    fun startActivityTracking(exercise: Exercise) {
        pendingTrackExercise = exercise
        requestLocation()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Tab roots live inside LogEzApp's Scaffold, whose innerPadding already pushes this
            // whole NavHost below the status bar — TopAppBar's default windowInsets would re-apply
            // the status-bar inset and double the empty space above the header, so it is zeroed.
            TopAppBar(
                title = { ScreenTitle(stringResource(R.string.workout_tab_title)) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                actions = {
                    IconButton(onClick = { showCreateFolder = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.workout_new_folder))
                    }
                    IconButton(onClick = { onCreateRoutine(null) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.workout_new_routine))
                    }
                },
            )
        },
        bottomBar = {
            // v4.0: Start Empty Workout is the tab's primary action, so it stays put instead of
            // scrolling away with the routines. LogEzApp's Scaffold already insets this whole
            // NavHost above the WorkoutMiniBar and the bottom NavigationBar, so this bar docks on
            // top of them rather than over them — and the mini-bar still owns "in-progress
            // workout, tap to resume" (§5.1.3), which is why no resume banner belongs here.
            // Page black to match the NavigationBar it docks on (2026-09-26); in the card tone it
            // read as a grey band between the list and the bar.
            Surface(color = MaterialTheme.colorScheme.background) {
                // Plain Button, not FilledTonalButton -- Owner: the CTA's own fill should be the
                // vibrant primary green, not FilledTonalButton's muted secondaryContainer default.
                val ctaShape = RoundedCornerShape(Radius.pill)
                Button(
                    onClick = { startEmpty() },
                    shape = ctaShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                ) {
                    Text(stringResource(R.string.workout_start_empty))
                }
            }
        },
    ) { padding ->
        // One scrollable list for the whole tab (Owner feedback: the heatmap/Goals used to be a
        // fixed, non-scrolling header sitting outside the folder/routine LazyColumn — scrolling
        // the routines left them permanently on screen, eating vertical space). Every section is
        // a LazyColumn item, so the tab scrolls as one; the Start button is the one deliberate
        // exception, pinned in bottomBar above.
        // M20a drag reorder. reorderFolders/reorderRoutines are fire-and-forget Room writes and
        // Reorderable's onMove fires on every hover swap expecting the list to already reflect the
        // move, so the tab keeps optimistic copies of each bucket (folders, each folder's routines,
        // root routines) that absorb swaps synchronously; the ViewModel gets exactly one call per
        // drop, from the handle's onDragStopped. One instance each for the screen's life, kept in
        // step by SyncOptimisticList (see it for why identity matters).
        val lazyListState = rememberLazyListState()
        val localFolders = remember { mutableStateListOf<FolderSection>().apply { addAll(uiState.folders) } }
        val localRootRoutines = remember { mutableStateListOf<RoutineCardModel>().apply { addAll(uiState.rootRoutines) } }
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            reorderTabRows(localFolders, localRootRoutines, fromKey, toKey)
        }
        SyncOptimisticList(localFolders, uiState.folders, reorderState.isAnyItemDragging)
        SyncOptimisticList(localRootRoutines, uiState.rootRoutines, reorderState.isAnyItemDragging)
        // While a folder header is being dragged every folder's routines are hidden, so the only
        // hover targets are other headers (and the root rows, which reorderTabRows refuses). Left
        // visible, the header's own routines sit right under it and each refused hover stalls the
        // library ~1s (it waits for a layout change that never comes), and a header crossing a
        // neighbour's routines would leapfrog that folder on every card centre it passes.
        //
        // The same ~1s stall reaches the OTHER two drag directions too, just without a visibility
        // fix available: reorderTabRows refuses a "routine" drop onto anything but a same-folder
        // sibling, and a "root" drop onto anything but another root row (see reorderTabRows below),
        // so hovering a routine card over a different folder, a folder header, or a root row -- or
        // a root row over a folder header or any routine card -- refuses and stalls the same way.
        // draggingBucket names which bucket is currently being dragged ("folder", "routine:<folder
        // id>", or "root") so every OTHER bucket's rows can be marked enabled = false for the
        // drag's duration: Reorderable's own hover/collision detection then skips them as swap
        // candidates entirely, instead of registering the hover and having reorderTabRows refuse it
        // a frame later (found in the M20a-h code audit, 2026-09-08 -- the folder-drag case above
        // was already mitigated; the routine and root directions were not).
        var draggingBucket by remember { mutableStateOf<String?>(null) }
        val commitFolders = { viewModel.reorderFolders(localFolders.map { it.folder.id }) }
        val commitFolderRoutines = { routineId: String ->
            // Re-resolve the owner: the optimistic list may have replaced the section.
            val owner = localFolders.firstOrNull { f -> f.routines.any { it.routine.id == routineId } }
            if (owner != null) viewModel.reorderRoutines(owner.routines.map { it.routine.id })
        }
        val commitRoot = { viewModel.reorderRoutines(localRootRoutines.map { it.routine.id }) }
        // Accessibility "Move up/down" (DragHandle custom actions): one slot within the row's own
        // bucket via the same swap step the drag uses, then the matching commit.
        fun nudge(key: String, neighbourKey: String?, commit: () -> Unit) {
            if (neighbourKey == null) return
            reorderTabRows(localFolders, localRootRoutines, key, neighbourKey)
            commit()
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            // The pinned bar's height rides in contentPadding rather than the modifier, so the
            // last routine card can scroll clear of it instead of sitting behind it forever.
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
        ) {
            // Owner, 2026-09-03: both are now Settings-toggleable (default on, so this changes
            // nothing until a user actually turns one off).
            if (uiState.showHeatmap) {
                item {
                    // M8c: progress heatmap at the very top, before any workout feature — a passive
                    // overview, not something the user acts on, so it never competes with
                    // Start/routines for the first tap.
                    LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.workout_heatmap_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                // Owner-requested redesign pass: the streak used to only surface on
                                // the finish summary, several taps deep — absent entirely (not a "0
                                // weeks" chip) until there's a real streak to show, same
                                // honest-empty-state rule as every other card on this tab. Day and
                                // week streaks are independent chips (a broken day streak with an
                                // intact week streak is a real, common state) rather than one
                                // combined figure.
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    if (uiState.dailyStreak > 0) {
                                        StreakChip(uiState.dailyStreak, R.plurals.summary_day_streak_value)
                                    }
                                    if (uiState.weeklyStreak > 0) {
                                        StreakChip(uiState.weeklyStreak, R.plurals.summary_streak_value)
                                    }
                                }
                            }
                            HeatmapGrid(
                                countsByDate = uiState.heatmapCounts,
                                today = uiState.heatmapToday,
                                firstDayOfWeek = uiState.heatmapFirstDayOfWeek,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                            )
                        }
                    }
                }
            }

            // M21: absent entirely (not a zeroed "0 steps" scorecard) whenever Health Connect has
            // nothing to show -- same graceful-degrade rule as the Profile wellness card.
            todaySteps?.let { steps ->
                item {
                    StepsScorecard(steps, recentSteps)
                }
            }

            if (uiState.showGoals) {
                item {
                    // M8d: Goals card, right below the heatmap — both are "progress" widgets, kept
                    // together above the actionable Start/routines content.
                    GoalsSection(
                        uiState = goalsUiState,
                        onCreateGoal = goalsViewModel::createGoal,
                        onDeleteGoal = goalsViewModel::deleteGoal,
                    )
                }
            }

            // M21a: only rendered once the two seed rows it needs actually resolve (see
            // WorkoutTabViewModel.quickTrackExercises) -- if a user has renamed/deleted "Running
            // (Outdoor)"/"Walking (Outdoor)" the card degrades to simply not appearing, rather than
            // pointing "Track a walk/run" at a stale id.
            quickTrackExercises?.let { exercises ->
                item {
                    QuickTrackCard(exercises = exercises, onTrack = ::startActivityTracking)
                }
            }

            if (uiState.isLoading) return@LazyColumn

            if (uiState.folders.isEmpty() && uiState.rootRoutines.isEmpty()) {
                item {
                    EmptyState(
                        icon = LogEzIcons.Workout,
                        title = stringResource(R.string.workout_empty_title),
                        subtitle = stringResource(R.string.workout_empty_subtitle),
                        ctaLabel = stringResource(R.string.workout_create_first_routine),
                        onCtaClick = { onCreateRoutine(null) },
                    )
                }
                return@LazyColumn
            }

            // Every draggable row is its own keyed item with a bucket prefix ("folder:", "routine:"
            // for a folder's routines, "root:") so onMove can tell buckets apart and refuse
            // cross-bucket drops -- routines used to render inside their folder's single item slot.
            // animateItemModifier = Modifier: no sibling-slide (near-zero-motion rule). Declined at
            // the M20a checkpoint (decisions.md 2026-09-07) -- a settled decision, not an open
            // question.
            localFolders.forEach { section ->
                item(key = "folder:${section.folder.id}") {
                    ReorderableItem(
                        reorderState,
                        key = "folder:${section.folder.id}",
                        enabled = draggingBucket == null || draggingBucket == "folder",
                        animateItemModifier = Modifier,
                    ) { isDragging ->
                        FolderHeaderRow(
                            folder = section.folder,
                            isCollapsed = section.folder.id in collapsedFolders,
                            isDragging = isDragging,
                            dragHandle = {
                                val index = localFolders.indexOfFirst { it.folder.id == section.folder.id }
                                val key = "folder:${section.folder.id}"
                                DragHandle(
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = { draggingBucket = "folder" },
                                        onDragStopped = { commitFolders(); draggingBucket = null },
                                    ),
                                    onMoveUp = if (index > 0) ({ nudge(key, "folder:${localFolders[index - 1].folder.id}", commitFolders) }) else null,
                                    onMoveDown = if (index in 0 until localFolders.lastIndex) ({ nudge(key, "folder:${localFolders[index + 1].folder.id}", commitFolders) }) else null,
                                )
                            },
                            onToggleCollapse = {
                                collapsedFolders = if (section.folder.id in collapsedFolders) collapsedFolders - section.folder.id else collapsedFolders + section.folder.id
                            },
                            onRename = { renamingFolder = section.folder },
                            onAddRoutine = { onCreateRoutine(section.folder.id) },
                            onDelete = { deletingFolder = section.folder },
                        )
                    }
                }
                // Hidden for the duration of ANY folder drag (not just this section's own), not only
                // when collapsed: a folder header dragging past a visible routine card would target
                // that routine's owner folder mid-drag (a hover the swap step refuses as cross-bucket
                // or as a folder-over-own-routine no-op), and Reorderable blocks ~1s waiting for a
                // layout change that a refused swap never produces -- worse, the header's own slot
                // then jumps by the hidden block's height on every accepted swap while a routine
                // card's centre stays under the still-tracked drag rect, which can re-trigger the
                // swap and ping-pong the two folders. Hiding routines during a folder drag leaves
                // only other folder headers as hover targets, which reorderTabRows resolves cleanly.
                if (section.folder.id !in collapsedFolders && draggingBucket != "folder") {
                    items(items = section.routines, key = { "routine:${it.routine.id}" }) { card ->
                        ReorderableItem(
                            reorderState,
                            key = "routine:${card.routine.id}",
                            enabled = draggingBucket == null || draggingBucket == "routine:${section.folder.id}",
                            animateItemModifier = Modifier,
                        ) { isDragging ->
                            RoutineCard(
                                card = card,
                                isDragging = isDragging,
                                dragHandle = {
                                    val owner = localFolders.firstOrNull { f -> f.routines.any { it.routine.id == card.routine.id } }
                                    val siblings = owner?.routines.orEmpty()
                                    val index = siblings.indexOfFirst { it.routine.id == card.routine.id }
                                    val key = "routine:${card.routine.id}"
                                    val commit = { commitFolderRoutines(card.routine.id) }
                                    DragHandle(
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = { draggingBucket = "routine:${owner?.folder?.id}" },
                                            onDragStopped = { commit(); draggingBucket = null },
                                        ),
                                        onMoveUp = if (index > 0) ({ nudge(key, "routine:${siblings[index - 1].routine.id}", commit) }) else null,
                                        onMoveDown = if (index in 0 until siblings.lastIndex) ({ nudge(key, "routine:${siblings[index + 1].routine.id}", commit) }) else null,
                                    )
                                },
                                onClick = { onRoutineClick(card.routine.id) },
                                onStart = { startRoutine(card.routine.id) },
                                onEdit = { onEditRoutine(card.routine.id) },
                                onDuplicate = { scope.launch { viewModel.duplicateRoutine(card.routine.id) } },
                                onMove = { movingRoutineId = card.routine.id },
                                onDelete = { deletingRoutineId = card.routine.id },
                                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
                            )
                        }
                    }
                }
            }

            if (localRootRoutines.isNotEmpty()) {
                item(key = "root-header") {
                    Text(
                        stringResource(R.string.workout_my_routines_header),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
                items(items = localRootRoutines, key = { "root:${it.routine.id}" }) { card ->
                    ReorderableItem(
                        reorderState,
                        key = "root:${card.routine.id}",
                        enabled = draggingBucket == null || draggingBucket == "root",
                        animateItemModifier = Modifier,
                    ) { isDragging ->
                        RoutineCard(
                            card = card,
                            isDragging = isDragging,
                            dragHandle = {
                                val index = localRootRoutines.indexOfFirst { it.routine.id == card.routine.id }
                                val key = "root:${card.routine.id}"
                                DragHandle(
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = { draggingBucket = "root" },
                                        onDragStopped = { commitRoot(); draggingBucket = null },
                                    ),
                                    onMoveUp = if (index > 0) ({ nudge(key, "root:${localRootRoutines[index - 1].routine.id}", commitRoot) }) else null,
                                    onMoveDown = if (index in 0 until localRootRoutines.lastIndex) ({ nudge(key, "root:${localRootRoutines[index + 1].routine.id}", commitRoot) }) else null,
                                )
                            },
                            onClick = { onRoutineClick(card.routine.id) },
                            onStart = { startRoutine(card.routine.id) },
                            onEdit = { onEditRoutine(card.routine.id) },
                            onDuplicate = { scope.launch { viewModel.duplicateRoutine(card.routine.id) } },
                            onMove = { movingRoutineId = card.routine.id },
                            onDelete = { deletingRoutineId = card.routine.id },
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
    }

    WorkoutTabDialogs(
        uiState = uiState,
        viewModel = viewModel,
        showCreateFolderState = showCreateFolderState,
        renamingFolderState = renamingFolderState,
        deletingFolderState = deletingFolderState,
        deletingRoutineIdState = deletingRoutineIdState,
        movingRoutineIdState = movingRoutineIdState,
        pendingStartState = pendingStartState,
        inProgressWorkoutId = inProgressWorkoutId,
        startSession = startSession,
        onNavigateToActivityTracking = onNavigateToActivityTracking,
        onNavigateToFinish = onNavigateToFinish,
        scope = scope,
        context = context,
    )
}

/**
 * The 6 trailing folder/routine dialogs `WorkoutTabScreen` can show — extracted as pure code
 * motion (2026-09-19 debt audit finding #21, same shape as WorkoutLoggerScreen's
 * WorkoutLoggerDialogs) so the main composable's body doesn't have to hold all of this leaf UI in
 * the same scope as the drag-reorderable folder/routine list. Each state param is the actual
 * `MutableState` the caller declared, not a value+setter pair, so this stays a single source of
 * truth with the parent, not a copy.
 */
@Composable
private fun WorkoutTabDialogs(
    uiState: WorkoutTabUiState,
    viewModel: WorkoutTabViewModel,
    showCreateFolderState: MutableState<Boolean>,
    renamingFolderState: MutableState<RoutineFolderEntity?>,
    deletingFolderState: MutableState<RoutineFolderEntity?>,
    deletingRoutineIdState: MutableState<String?>,
    movingRoutineIdState: MutableState<String?>,
    pendingStartState: MutableState<PendingStart?>,
    inProgressWorkoutId: String?,
    startSession: (String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (String) -> Unit,
    scope: CoroutineScope,
    context: Context,
) {
    var showCreateFolder by showCreateFolderState
    var renamingFolder by renamingFolderState
    var deletingFolder by deletingFolderState
    var deletingRoutineId by deletingRoutineIdState
    var movingRoutineId by movingRoutineIdState
    var pendingStart by pendingStartState
    var interruptedRun by remember { mutableStateOf<Pair<String, Long>?>(null) }

    interruptedRun?.let { (workoutId, startedAt) ->
        InterruptedTrackingDialog(
            workoutId = workoutId,
            startedAt = startedAt,
            onKeptTime = { id -> interruptedRun = null; onNavigateToFinish(id) },
            onDiscarded = { interruptedRun = null },
        )
    }

    if (showCreateFolder) {
        TextInputDialog(
            title = stringResource(R.string.workout_new_folder),
            initialValue = "",
            onConfirm = { viewModel.createFolder(it); showCreateFolder = false },
            onDismiss = { showCreateFolder = false },
        )
    }
    renamingFolder?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.workout_rename_folder),
            initialValue = folder.name,
            onConfirm = { viewModel.renameFolder(folder.id, it); renamingFolder = null },
            onDismiss = { renamingFolder = null },
        )
    }
    deletingFolder?.let { folder ->
        ConfirmDialog(
            onDismissRequest = { deletingFolder = null },
            title = stringResource(R.string.workout_delete_folder_title),
            body = stringResource(R.string.workout_delete_folder_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteFolder(folder) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
    deletingRoutineId?.let { routineId ->
        ConfirmDialog(
            onDismissRequest = { deletingRoutineId = null },
            title = stringResource(R.string.workout_delete_routine_title),
            body = stringResource(R.string.workout_delete_routine_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteRoutine(routineId) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
    movingRoutineId?.let { routineId ->
        MoveToFolderDialog(
            folders = uiState.folders.map { it.folder },
            onSelect = { folderId -> viewModel.moveRoutineToFolder(routineId, folderId); movingRoutineId = null },
            onDismiss = { movingRoutineId = null },
        )
    }

    pendingStart?.let { pending ->
        val existingId = inProgressWorkoutId
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text(stringResource(R.string.workout_resume_title)) },
            text = { Text(stringResource(R.string.workout_resume_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingStart = null
                    scope.launch {
                        val info = viewModel.inProgressWorkoutInfo()
                        when {
                            info == null -> Unit
                            info.kind != WorkoutKind.GPS_TRACKED -> existingId?.let(startSession)
                            // Still collecting: re-enter the tracking screen, start nothing.
                            viewModel.isGpsSessionAlive() -> onNavigateToActivityTracking()
                            // The process died mid-run: route and distance are gone, so the user
                            // has to decide what happens to the row.
                            else -> interruptedRun = info.id to info.startedAt
                        }
                    }
                }) {
                    Text(stringResource(R.string.workout_resume_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingStart = null
                    scope.launch {
                        when (pending) {
                            is PendingStart.Empty -> startSession(viewModel.discardInProgressAndStartEmpty())
                            is PendingStart.Routine -> startSession(viewModel.discardInProgressAndStartRoutine(pending.routineId))
                            is PendingStart.ActivityTracking -> {
                                viewModel.discardInProgressAndStartActivityTracking(pending.exerciseId, pending.title)
                                startActivityTrackingService(context)
                                onNavigateToActivityTracking()
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.workout_resume_discard_action))
                }
            },
        )
    }
}

@Composable
private fun FolderHeaderRow(
    folder: RoutineFolderEntity,
    isCollapsed: Boolean,
    isDragging: Boolean,
    dragHandle: @Composable () -> Unit,
    onToggleCollapse: () -> Unit,
    onRename: () -> Unit,
    onAddRoutine: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(color = if (isDragging) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleCollapse).padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dragHandle()
            Icon(if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, contentDescription = null)
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = Spacing.xs))
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workout_add_routine_to_folder)) }, onClick = { menuExpanded = false; onAddRoutine() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}


/**
 * M20a: applies one hover-swap from Reorderable's `onMove` to the tab's optimistic lists. Keys are
 * bucket-prefixed (`folder:`, `routine:` for a folder's routines, `root:`); a move whose two keys
 * belong to different buckets (or to different folders) is refused, because the DAO writers stamp
 * `orderIndex` for exactly the ids they are handed and cross-folder moves need
 * `moveRoutineToFolder` semantics instead. Dragging a folder over another folder's routine targets
 * that folder's position, so a folder can be dragged past an expanded neighbour.
 */
internal fun reorderTabRows(
    folders: MutableList<FolderSection>,
    rootRoutines: MutableList<RoutineCardModel>,
    fromKey: String,
    toKey: String,
) {
    val (fromKind, fromId) = fromKey.split(":", limit = 2).takeIf { it.size == 2 } ?: return
    val (toKind, toId) = toKey.split(":", limit = 2).takeIf { it.size == 2 } ?: return
    fun ownerFolderId(routineId: String): String? = folders.firstOrNull { f -> f.routines.any { it.routine.id == routineId } }?.folder?.id
    when (fromKind) {
        "folder" -> {
            val targetFolderId = when (toKind) {
                "folder" -> toId
                "routine" -> ownerFolderId(toId)
                else -> null
            } ?: return
            val fromIndex = folders.indexOfFirst { it.folder.id == fromId }
            val toIndex = folders.indexOfFirst { it.folder.id == targetFolderId }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) folders.add(toIndex, folders.removeAt(fromIndex))
        }
        "routine" -> {
            if (toKind != "routine") return
            val ownerIndex = folders.indexOfFirst { f -> f.routines.any { it.routine.id == fromId } }
            if (ownerIndex < 0) return
            val owner = folders[ownerIndex]
            val fromIndex = owner.routines.indexOfFirst { it.routine.id == fromId }
            val toIndex = owner.routines.indexOfFirst { it.routine.id == toId }
            if (fromIndex < 0 || toIndex < 0) return // toId lives in another folder -> refuse
            val reordered = owner.routines.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            folders[ownerIndex] = owner.copy(routines = reordered)
        }
        "root" -> {
            if (toKind != "root") return
            val fromIndex = rootRoutines.indexOfFirst { it.routine.id == fromId }
            val toIndex = rootRoutines.indexOfFirst { it.routine.id == toId }
            if (fromIndex >= 0 && toIndex >= 0) rootRoutines.add(toIndex, rootRoutines.removeAt(fromIndex))
        }
    }
}

@Composable
private fun RoutineCard(
    card: RoutineCardModel,
    isDragging: Boolean,
    dragHandle: @Composable () -> Unit,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    LogEzCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = if (isDragging) Elevation.dragging else Elevation.card,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dragHandle()
                Text(card.routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (card.routine.structure == WorkoutStructure.CIRCUIT) {
                    CircuitChip(modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            if (card.routine.structure == WorkoutStructure.CIRCUIT) {
                // M11 circuit preview line: "N rounds · M exercises".
                Text(
                    pluralStringResource(R.plurals.routine_rounds_count, card.rounds, card.rounds) +
                        " · " +
                        pluralStringResource(R.plurals.routine_exercises_count, card.exerciseCount, card.exerciseCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            if (card.exercisePreview.isNotBlank()) {
                Text(card.exercisePreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onStart) { Text(stringResource(R.string.workout_start_routine)) }
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.workout_move_to_folder)) }, onClick = { menuExpanded = false; onMove() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

/** M21a "Track a walk/run": Running/Walking is a single tap; GPS tracking + distance/duration capture happen on the live-tracking screen. */
@Composable
private fun QuickTrackCard(exercises: QuickTrackExercises, onTrack: (Exercise) -> Unit) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(stringResource(R.string.workout_track_walk_run_title), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(onClick = { onTrack(exercises.running) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workout_track_walk_run_running))
                }
                OutlinedButton(onClick = { onTrack(exercises.walking) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workout_track_walk_run_walking))
                }
            }
        }
    }
}

/**
 * Same v4.0 pill/mono-caps chip vocabulary [CircuitChip] established (primary-tinted fill,
 * [LogEzMono] text) — reused here rather than invented fresh, so a status chip reads the same
 * wherever one appears on the tab.
 */
@Composable
private fun StreakChip(value: Int, pluralsRes: Int) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    ) {
        Text(
            pluralStringResource(pluralsRes, value, value),
            style = LogEzMono.dataSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/** M21: today's step count, sourced from Health Connect -- a passive scorecard, same visual weight as the heatmap card above it. Never rendered at all when Health Connect has nothing to show (see the `todaySteps?.let` call site). */
@Composable
private fun StepsScorecard(steps: Long, recentSteps: List<DailyStepCount>) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.workout_steps_scorecard_title),
                    style = LogEzMono.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "%,d".format(Locale.ROOT, steps),
                    style = LogEzMono.dataLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (recentSteps.isNotEmpty()) {
                SevenDayStepsChart(recentSteps, Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun SevenDayStepsChart(steps: List<DailyStepCount>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val todayColor = MaterialTheme.colorScheme.primary
    val maxSteps = steps.maxOfOrNull { it.steps }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = stringResource(R.string.workout_steps_last_seven_days),
            style = LogEzMono.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            val slotWidth = size.width / steps.size
            val barWidth = slotWidth * 0.48f
            val minimumBarHeight = 2.dp.toPx()
            steps.forEachIndexed { index, day ->
                val height = if (day.steps == 0L) minimumBarHeight else {
                    (size.height * day.steps.toFloat() / maxSteps.toFloat()).coerceAtLeast(minimumBarHeight)
                }
                drawRoundRect(
                    color = if (index == steps.lastIndex) todayColor else barColor,
                    topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            steps.forEach { day ->
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, currentLocale()),
                    style = LogEzMono.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(title: String, initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MoveToFolderDialog(folders: List<RoutineFolderEntity>, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_move_to_folder)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.workout_my_routines_header),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = Spacing.sm),
                )
                folders.forEach { folder ->
                    Text(folder.name, modifier = Modifier.fillMaxWidth().clickable { onSelect(folder.id) }.padding(vertical = Spacing.sm))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
