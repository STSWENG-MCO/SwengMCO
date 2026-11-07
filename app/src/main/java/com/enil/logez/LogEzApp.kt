package com.enil.logez

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enil.logez.app.navigation.LogEzDestination
import com.enil.logez.app.navigation.LogEzNavHost
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.feature.activity.ActivityTrackingRoutes
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.workout.WorkoutMiniBar
import com.enil.logez.feature.workout.WorkoutRoutes
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** App root: theme + the 3-tab Scaffold. Theme is dark-only (Owner directive) — no mode switching. */
@Composable
fun LogEzApp() {
    LogEzTheme {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val resumedSnackbarMessage = stringResource(R.string.workout_mini_bar_resumed_snackbar)

        // §9.5 cold-start recovery: process/service died (swipe-from-recents, force-stop, crash)
        // while a workout was IN_PROGRESS -- Room already has the data; this restarts the service
        // and lands the user back in the right screen, exactly once per cold start.
        val startupViewModel: AppStartupViewModel = hiltViewModel()
        val recovery by startupViewModel.recovery.collectAsStateWithLifecycle()
        val trackingResumedMessage = stringResource(R.string.activity_tracking_resumed_snackbar)
        var interruptedRun by remember { mutableStateOf<StartupRecovery.InterruptedRun?>(null) }
        val resumeRecoveredSession = rememberStartWorkoutSession(onNavigateToLogger = { workoutId ->
            navController.navigate(WorkoutRoutes.logger(workoutId))
        })
        LaunchedEffect(recovery) {
            // Only the strength branch may go through rememberStartWorkoutSession -- that is what
            // starts WorkoutSessionService, and doing it for a GPS run puts a second foreground
            // service alongside the location one that is already running.
            when (val r = recovery) {
                is StartupRecovery.None -> Unit
                is StartupRecovery.ResumeStrength -> {
                    startupViewModel.consumeRecovery()
                    resumeRecoveredSession(r.workoutId)
                    snackbarHostState.showSnackbar(resumedSnackbarMessage)
                }
                is StartupRecovery.ResumeLiveTracking -> {
                    startupViewModel.consumeRecovery()
                    // Start nothing: ActivityTrackingService is already foregrounded and collecting.
                    navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING)
                    snackbarHostState.showSnackbar(trackingResumedMessage)
                }
                is StartupRecovery.InterruptedRun -> {
                    startupViewModel.consumeRecovery()
                    interruptedRun = r
                }
            }
        }

        interruptedRun?.let { run ->
            InterruptedTrackingDialog(
                workoutId = run.workoutId,
                startedAt = run.startedAt,
                onKeptTime = { workoutId ->
                    interruptedRun = null
                    navController.navigate(WorkoutRoutes.finish(workoutId))
                },
                onDiscarded = { interruptedRun = null },
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                // Spine navigation rule: only the three tab roots ever show the bottom bar —
                // every sub-screen (exercise library/detail/editor, and more as later
                // milestones land) hides it.
                val onTabRoot = LogEzDestination.entries.any { destination ->
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                }

                if (onTabRoot) {
                    Column {
                        WorkoutMiniBar(
                            onExpand = { workoutId -> navController.navigate(WorkoutRoutes.logger(workoutId)) },
                            onExpandLiveTracking = { navController.navigate(ActivityTrackingRoutes.LIVE_TRACKING) },
                            // Same dialog state the startup path raises, so there is one dialog
                            // and one resolution path regardless of how the run was reached.
                            onInterruptedRun = { workoutId, startedAt ->
                                interruptedRun = StartupRecovery.InterruptedRun(workoutId, startedAt)
                            },
                        )
                        // Owner, 2026-09-26: the bar is page black, not the grey card tone.
                        // NavigationBar defaults to surfaceContainer, which Theme.kt maps to the
                        // card colour, so a grey block sat under every tab. The app draws edge to
                        // edge, so this colour also fills the area behind the gesture handle.
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                            tonalElevation = 0.dp,
                        ) {
                            LogEzDestination.entries.forEach { destination ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == destination.route
                                } == true

                                NavigationBarItem(
                                    selected = selected,
                                    // Material3's default selected color is `secondary` (DeepGreen
                                    // -- deliberately muted, see ADR-0003), which read as dull next
                                    // to the vibrant primary used everywhere else. The active tab
                                    // should be the SAME vibrant green as the CTAs and chart fills.
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        // Null: the visible label already names the tab, and a
                                        // description here made TalkBack read it twice.
                                        Icon(imageVector = destination.icon, contentDescription = null)
                                    },
                                    label = { Text(stringResource(destination.labelRes)) },
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            // The workout summary draws behind the status bar so a walk/run's route map can run up
            // to the top edge (2026-09-26); it pads its own text back down. Every other screen
            // keeps the full inset.
            val backStackEntry by navController.currentBackStackEntryAsState()
            val drawsBehindStatusBar = backStackEntry?.destination?.route == WorkoutRoutes.SUMMARY
            val layoutDirection = LocalLayoutDirection.current
            val contentPadding = if (drawsBehindStatusBar) {
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding(),
                )
            } else {
                innerPadding
            }
            // Tablets, foldables and landscape (2026-09-25, Play-readiness audit): the phone layout
            // used to stretch edge to edge, so cards and set tables spread across a whole tablet.
            // Content is capped at a readable width and centred; the bottom bar stays full width.
            // A phone is narrower than the cap, so nothing changes there.
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                LogEzNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
                )
            }
        }
    }
}

/** The widest the content column grows on a large screen: comfortable for reading and for the set table. */
private val MAX_CONTENT_WIDTH = 720.dp
