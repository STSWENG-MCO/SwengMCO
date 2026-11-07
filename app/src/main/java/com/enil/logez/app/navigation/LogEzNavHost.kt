package com.enil.logez.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.enil.logez.feature.activity.ActivityTrackingRoutes
import com.enil.logez.feature.activity.ActivityTrackingScreen
import com.enil.logez.feature.analytics.AnalyticsRoutes
import com.enil.logez.feature.analytics.AnalyticsScreen
import com.enil.logez.feature.analytics.AnalyticsViewModel
import com.enil.logez.feature.analytics.MonthlyReportScreen
import com.enil.logez.feature.analytics.ProfileScreen
import com.enil.logez.feature.exercises.CustomExerciseEditorScreen
import com.enil.logez.feature.exercises.ExerciseDetailScreen
import com.enil.logez.feature.exercises.ExerciseLibraryScreen
import com.enil.logez.feature.exercises.ExerciseRoutes
import com.enil.logez.feature.history.CalendarScreen
import com.enil.logez.feature.history.HistoryRoutes
import com.enil.logez.feature.history.HistoryScreen
import com.enil.logez.feature.history.WorkoutDetailScreen
import com.enil.logez.feature.measurements.CameraCaptureScreen
import com.enil.logez.feature.measurements.MeasurementsRoutes
import com.enil.logez.feature.measurements.MeasurementsScreen
import com.enil.logez.feature.routines.RoutineBuilderScreen
import com.enil.logez.feature.routines.RoutineDetailScreen
import com.enil.logez.feature.routines.RoutineRoutes
import com.enil.logez.feature.routines.WorkoutTabScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.enil.logez.feature.settings.DataScreen
import com.enil.logez.feature.settings.PlateEquipmentScreen
import com.enil.logez.feature.settings.SettingsRoutes
import com.enil.logez.feature.settings.SettingsScreen
import com.enil.logez.feature.settings.SoundsSettingsScreen
import com.enil.logez.feature.settings.WarmupSetsScreen
import com.enil.logez.feature.workout.WorkoutLoggerScreen
import com.enil.logez.feature.workout.WorkoutLoggerViewModel
import com.enil.logez.feature.workout.WorkoutRoutes
import com.enil.logez.feature.workout.finish.FinishWorkoutScreen
import com.enil.logez.feature.workout.finish.WorkoutSummaryScreen
import com.enil.logez.feature.settings.LicensesScreen

@Composable
fun LogEzNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LogEzDestination.History.route,
        modifier = modifier,
        // Owner directive (2026-08-25): zero perceptible latency on tab/screen switches — no
        // slide/fade default transition, regardless of what Navigation-Compose ships by default.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(LogEzDestination.History.route) {
            HistoryScreen(
                onWorkoutClick = { id -> navController.navigate(HistoryRoutes.detail(id)) },
                onStartWorkout = {
                    navController.navigate(LogEzDestination.Workout.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(
            route = HistoryRoutes.DETAIL,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            WorkoutDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(WorkoutRoutes.edit(id)) },
                onSavedAsRoutine = { routineId -> navController.navigate(RoutineRoutes.builder(routineId = routineId)) },
                onNavigateToLogger = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onNavigateToActivityTracking = { navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING) },
                onNavigateToFinish = { workoutId -> navController.navigate(WorkoutRoutes.finish(workoutId)) },
            )
        }
        composable(HistoryRoutes.CALENDAR) {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                onWorkoutClick = { id -> navController.navigate(HistoryRoutes.detail(id)) },
            )
        }
        composable(LogEzDestination.Workout.route) {
            WorkoutTabScreen(
                onRoutineClick = { id -> navController.navigate(RoutineRoutes.detail(id)) },
                onCreateRoutine = { folderId -> navController.navigate(RoutineRoutes.builder(folderId = folderId)) },
                onEditRoutine = { id -> navController.navigate(RoutineRoutes.builder(routineId = id)) },
                onNavigateToLogger = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
                onNavigateToActivityTracking = { navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING) },
                onNavigateToFinish = { workoutId -> navController.navigate(WorkoutRoutes.finish(workoutId)) },
            )
        }
        composable(ActivityTrackingRoutes.LIVE_TRACKING) {
            ActivityTrackingScreen(
                // M21 redesign (2026-09-11): straight to Save Workout, never through the Logger —
                // see ActivityTrackingScreen's own doc comment and the FINISH composable below,
                // whose onSaved/onDiscardInstead now handle arriving from either this route or the
                // Logger.
                onFinished = { workoutId ->
                    navController.navigate(WorkoutRoutes.finish(workoutId)) {
                        popUpTo(ActivityTrackingRoutes.LIVE_TRACKING) { inclusive = true }
                    }
                },
                onCancelled = { navController.popBackStack() },
            )
        }
        composable(LogEzDestination.Profile.route) {
            ProfileScreen(
                onExercisesClick = { navController.navigate(ExerciseRoutes.library()) },
                onCalendarClick = { navController.navigate(HistoryRoutes.CALENDAR) },
                onStatisticsClick = { metric -> navController.navigate(AnalyticsRoutes.dashboard(focus = metric?.name)) },
                onMeasurementsClick = { navController.navigate(MeasurementsRoutes.MEASUREMENTS) },
                onSettingsClick = { navController.navigate(SettingsRoutes.SETTINGS) { launchSingleTop = true } },
            )
        }

        // M22a: the only screen so far that hands a value back to its caller rather than just
        // popping -- CameraCaptureScreen writes the captured photo's path onto the PREVIOUS back
        // stack entry's own SavedStateHandle, and Measurements reads it back as a StateFlow so a
        // configuration change or process death between capture and consumption doesn't lose it.
        composable(MeasurementsRoutes.MEASUREMENTS) { backStackEntry ->
            val capturedPhotoPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>(MeasurementsRoutes.CAPTURED_PHOTO_URI_KEY, null)
                .collectAsStateWithLifecycle()
            MeasurementsScreen(
                onBack = { navController.popBackStack() },
                onOpenCamera = { navController.navigate(MeasurementsRoutes.CAMERA_CAPTURE) },
                capturedPhotoPath = capturedPhotoPath,
                onCapturedPhotoConsumed = { backStackEntry.savedStateHandle.remove<String>(MeasurementsRoutes.CAPTURED_PHOTO_URI_KEY) },
            )
        }
        composable(MeasurementsRoutes.CAMERA_CAPTURE) {
            CameraCaptureScreen(
                onCaptured = { uri ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(MeasurementsRoutes.CAPTURED_PHOTO_URI_KEY, uri.toString())
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        // M16: reachable from Profile and from the live Logger's overflow menu. A plain navigate
        // keeps whatever pushed it (Logger included) alive beneath it on the back stack, so
        // logger -> settings -> back lands back in the untouched session (write-through +
        // foreground service — no state to preserve beyond the destination itself).
        composable(SettingsRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSoundsClick = { navController.navigate(SettingsRoutes.SOUNDS) { launchSingleTop = true } },
                onPlateEquipmentClick = { navController.navigate(SettingsRoutes.PLATE_EQUIPMENT) { launchSingleTop = true } },
                onWarmupSetsClick = { navController.navigate(SettingsRoutes.WARMUP_SETS) { launchSingleTop = true } },
                onDataClick = { navController.navigate(SettingsRoutes.DATA) { launchSingleTop = true } },
                onLicensesClick = { navController.navigate(SettingsRoutes.LICENSES) { launchSingleTop = true } },
            )
        }
        composable(SettingsRoutes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.DATA) {
            DataScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.SOUNDS) {
            SoundsSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.PLATE_EQUIPMENT) {
            PlateEquipmentScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.WARMUP_SETS) {
            WarmupSetsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = AnalyticsRoutes.DASHBOARD,
            arguments = listOf(navArgument(AnalyticsViewModel.FOCUS_ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onMuscleClick = { muscle -> navController.navigate(ExerciseRoutes.library(muscle = muscle.name)) },
                onMonthlyReportClick = { navController.navigate(AnalyticsRoutes.MONTHLY_REPORT) },
            )
        }
        composable(AnalyticsRoutes.MONTHLY_REPORT) {
            MonthlyReportScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = ExerciseRoutes.LIBRARY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            ExerciseLibraryScreen(
                onBack = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
                onEditExercise = { id -> navController.navigate(ExerciseRoutes.editor(exerciseId = id)) },
            )
        }
        composable(
            route = ExerciseRoutes.DETAIL,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) {
            ExerciseDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(ExerciseRoutes.editor(exerciseId = id)) },
                onDeleted = { navController.popBackStack() },
                onDuplicated = { newId ->
                    navController.popBackStack()
                    navController.navigate(ExerciseRoutes.detail(newId))
                },
            )
        }
        composable(
            route = ExerciseRoutes.EDITOR,
            arguments = listOf(
                navArgument("exerciseId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("prefillName") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            CustomExerciseEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = RoutineRoutes.DETAIL,
            arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
        ) {
            RoutineDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(RoutineRoutes.builder(routineId = id)) },
                onNavigateToLogger = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
                onNavigateToActivityTracking = { navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING) },
                onNavigateToFinish = { workoutId -> navController.navigate(WorkoutRoutes.finish(workoutId)) },
            )
        }
        composable(
            route = RoutineRoutes.BUILDER,
            arguments = listOf(
                navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("folderId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            RoutineBuilderScreen(
                onBack = { navController.popBackStack() },
                onSaved = { routineId ->
                    navController.popBackStack()
                    if (navController.currentDestination?.route != RoutineRoutes.DETAIL) {
                        navController.navigate(RoutineRoutes.detail(routineId))
                    }
                },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
            )
        }

        composable(
            route = WorkoutRoutes.LOGGER,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            val workoutId = it.arguments?.getString("workoutId").orEmpty()
            WorkoutLoggerScreen(
                // M4b: the Logger is now reachable from any tab (mini-bar tap-to-expand) or
                // directly from a cold start (§9.5 recovery) -- a target-route pop assuming
                // "Workout" is always an ancestor in the back stack silently no-ops (and strands
                // the user on the finished/discarded Logger) whenever it isn't. A plain pop
                // always returns to whatever was actually beneath this destination.
                onExit = { navController.popBackStack() },
                onNavigateToFinish = {
                    navController.navigate(WorkoutRoutes.finish(workoutId)) { launchSingleTop = true }
                },
                onDiscarded = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
                onSettingsClick = { navController.navigate(SettingsRoutes.SETTINGS) { launchSingleTop = true } },
            )
        }

        composable(
            route = WorkoutRoutes.EDIT,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType },
                // The one thing that distinguishes this destination from LOGGER. Declared as a
                // defaulted nav argument rather than a route segment so the flag reaches
                // SavedStateHandle without putting "true" in the URL; LOGGER omits it entirely and
                // the ViewModel reads absent-as-false.
                navArgument(WorkoutLoggerViewModel.EDIT_MODE_ARG) { type = NavType.BoolType; defaultValue = true },
            ),
        ) {
            WorkoutLoggerScreen(
                // §5.1.10: Save and Cancel both return to Workout Detail, which re-reads on resume.
                onExit = { navController.popBackStack() },
                onNavigateToFinish = {}, // edit mode saves in place — there is no finish hand-off
                onDiscarded = { navController.popBackStack() },
                onExerciseClick = { id -> navController.navigate(ExerciseRoutes.detail(id)) },
                onCreateExercise = { prefill -> navController.navigate(ExerciseRoutes.editor(prefillName = prefill)) },
                onSettingsClick = { navController.navigate(SettingsRoutes.SETTINGS) { launchSingleTop = true } },
            )
        }

        composable(
            route = WorkoutRoutes.FINISH,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            FinishWorkoutScreen(
                // Back returns to the still-IN_PROGRESS Logger/live-tracking screen — nothing was
                // saved yet (§5.1.8).
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    // Drop whichever of the Logger or the live-tracking screen sits behind this
                    // one, plus this screen itself: the workout is COMPLETED, so none of them is a
                    // sane back destination from the summary. M21 redesign (2026-09-11): FINISH is
                    // now reachable from either ancestor (GPS tracking's Finish button skips the
                    // Logger entirely) — popBackStack(route, inclusive) is a no-op returning false
                    // when that route isn't actually on the back stack, so trying LOGGER first and
                    // falling back to LIVE_TRACKING covers both origins correctly.
                    val poppedLogger = navController.popBackStack(WorkoutRoutes.LOGGER, inclusive = true)
                    if (!poppedLogger) {
                        navController.popBackStack(ActivityTrackingRoutes.LIVE_TRACKING, inclusive = true)
                    }
                    navController.navigate(WorkoutRoutes.summary(savedId))
                },
                onDiscardInstead = {
                    // The workout has been deleted by now, so whichever screen sits behind this
                    // one is dead too — pop through it. A target pop at "workout" silently no-opped
                    // whenever the Logger was reached from the mini-bar or a cold start, which
                    // left the user sitting on the Save screen with nothing having happened.
                    val poppedLogger = navController.popBackStack(WorkoutRoutes.LOGGER, inclusive = true)
                    val poppedLiveTracking = !poppedLogger &&
                        navController.popBackStack(ActivityTrackingRoutes.LIVE_TRACKING, inclusive = true)
                    if (!poppedLogger && !poppedLiveTracking) {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(
            route = WorkoutRoutes.SUMMARY,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) {
            WorkoutSummaryScreen(
                // §5.1.8(c): "Done -> History tab".
                onDone = {
                    navController.navigate(LogEzDestination.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
