package com.enil.logez.feature.activity

import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.GeoDistance
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.activity.location.LocationFix
import com.enil.logez.feature.activity.location.LocationSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityTrackingState(
    val workoutId: String? = null,
    val workoutSetId: String? = null,
    val startedAtMillis: Long? = null,
    val distanceMeters: Double = 0.0,
    /** Same accepted-fix sequence [finishTracking] encodes into `route_polyline` -- exposed live
     * here too so the live tracking screen's map can draw it as it grows. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    /**
     * (epochMillis, cumulativeDistanceMeters) sampled roughly every [DISTANCE_HISTORY_INTERVAL_SECONDS]
     * of elapsed time while tracking -- the live screen derives a recent-window pace trend from
     * the deltas between consecutive samples ([PaceCalculator.paceSecondsPerUnit] called on each
     * pair, not on the cumulative total, which would just reproduce the lifetime-average pace
     * shown elsewhere). Not persisted, same v1 "no process-death rehydration" scope as [routePoints].
     */
    val distanceHistory: List<Pair<Long, Double>> = emptyList(),
) {
    val isTracking: Boolean get() = workoutId != null
}

data class FinishedTrack(val workoutId: String, val distanceMeters: Double, val durationSeconds: Int)

/**
 * M21a. Framework-free GPS session state machine, the same split-of-concerns as
 * [com.enil.logez.feature.workout.session.WorkoutSessionController] (Service = platform glue
 * only, Controller = the testable state machine), shared as a Hilt singleton between
 * `ActivityTrackingService` and the live-tracking ViewModel.
 *
 * v1 scope, deliberately: no pause/resume (a tracked run/walk is start-to-finish continuous) and
 * no process-death rehydration (if the process dies mid-run, the in-progress track is lost — a
 * known, accepted limitation, unlike `WorkoutSessionController`'s full recovery story).
 */
@Singleton
class ActivityTrackingController @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val activityTrackRepository: ActivityTrackRepository,
    private val locationSource: LocationSource,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ActivityTrackingState())
    val state: StateFlow<ActivityTrackingState> get() = _state

    // Mutated only from onFix, which only ever runs inside fixJob's single collector coroutine.
    // finishTracking() reads these after the accumulated total, so it must cancelAndJoin() the job
    // first to avoid racing a still-landing fix. cancelTracking() throws the accumulated state away
    // entirely rather than reading it, so a plain (non-suspending) cancel() is sufficient there.
    private val routePoints = mutableListOf<Pair<Double, Double>>()

    /** Seconds since start for each entry of [routePoints], appended together so the two never drift apart. */
    private val routeTimes = mutableListOf<Long>()
    private val distanceHistory = mutableListOf<Pair<Long, Double>>()
    private var lastHistorySampleElapsedSeconds = 0
    private var lastAccepted: LocationFix? = null
    private var accuracySumMeters = 0.0
    private var fixJob: Job? = null

    fun startTracking(workoutId: String, workoutSetId: String) {
        routePoints.clear()
        routeTimes.clear()
        distanceHistory.clear()
        lastHistorySampleElapsedSeconds = 0
        lastAccepted = null
        accuracySumMeters = 0.0
        _state.value = ActivityTrackingState(
            workoutId = workoutId,
            workoutSetId = workoutSetId,
            startedAtMillis = clock.now().toEpochMilliseconds(),
        )
        fixJob = scope.launch { locationSource.fixes().collect(::onFix) }
    }

    /** Accuracy/stationary-drift filtering (rev. 3 plan §2.3): discard poor fixes and near-zero movement. */
    private fun onFix(fix: LocationFix) {
        if (fix.accuracyMeters > MAX_ACCEPTABLE_ACCURACY_METERS) return
        val last = lastAccepted
        if (last != null) {
            val delta = GeoDistance.metersBetween(last.latitude, last.longitude, fix.latitude, fix.longitude)
            if (delta < MIN_MOVEMENT_METERS) return
            _state.update { it.copy(distanceMeters = it.distanceMeters + delta) }
        }
        lastAccepted = fix
        routePoints += fix.latitude to fix.longitude
        routeTimes += elapsedSeconds().toLong()
        accuracySumMeters += fix.accuracyMeters

        // Piggybacks on a real, externally-driven fix arrival rather than a self-ticking
        // `delay()` loop of its own: a `scope.launch { while (true) { delay(...) } }` started here
        // would run the instant startTracking() is called and keep running until explicitly
        // cancelled, which hangs any test that starts tracking without also finishing/cancelling
        // it before the test ends (runTest has no natural idle point to stop at) -- exactly the
        // "unbounded ticker hangs the test suite" bug liveHeartRateFlow's own doc comment records
        // happening once already, just with a Job instead of a cold Flow this time. Throttling to
        // one sample per DISTANCE_HISTORY_INTERVAL_SECONDS of real elapsed time here costs nothing
        // extra to test (fully driven by the same FakeLocationSource.emit() calls tests already
        // make) and, in practice, tracks close enough to wall-clock cadence while moving -- it
        // simply stops sampling during a full stop, which is the right behavior for a pace trend.
        // The very first accepted fix always seeds a sample, whatever elapsedSeconds() reads at
        // that instant (typically 0) -- the throttle below is for the samples after it, and an
        // elapsed-time comparison against a lastHistorySampleElapsedSeconds default of 0 would
        // otherwise silently swallow that seed sample too, since 0 - 0 is never >= the interval.
        val nowElapsed = elapsedSeconds()
        if (distanceHistory.isEmpty() || nowElapsed - lastHistorySampleElapsedSeconds >= DISTANCE_HISTORY_INTERVAL_SECONDS) {
            lastHistorySampleElapsedSeconds = nowElapsed
            distanceHistory += clock.now().toEpochMilliseconds() to _state.value.distanceMeters
        }

        // A defensive copy: both lists keep growing in place, so a stale emitted state must not
        // alias the same backing list a later fix would silently mutate out from under it.
        _state.update { it.copy(routePoints = routePoints.toList(), distanceHistory = distanceHistory.toList()) }
    }

    fun elapsedSeconds(nowMillis: Long = clock.now().toEpochMilliseconds()): Int {
        val startedAt = _state.value.startedAtMillis ?: return 0
        return ((nowMillis - startedAt) / 1000).toInt().coerceAtLeast(0)
    }

    /** Ticks once/sec while collected (cold — no cost when nobody's watching), mirroring `WorkoutSessionController.elapsedSecondsFlow`. */
    val elapsedSecondsFlow: Flow<Int> = flow {
        while (true) {
            if (_state.value.isTracking) emit(elapsedSeconds())
            delay(1_000)
        }
    }

    /**
     * Writes distance/duration onto the existing `WorkoutSetEntity` and persists the encoded
     * route. Returns `null` if no session was active.
     *
     * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
     * handing off into the strength Logger first (decisions.md same date) — the Logger was the
     * only place that used to mark this set `isCompleted` (its own checkmark tap) and freeze the
     * live elapsed time onto the parent `WorkoutEntity.durationSeconds`
     * (`WorkoutLoggerViewModel.prepareForFinish()`). Both now happen here instead, since nothing
     * downstream of this call visits the Logger anymore to do it: `WorkoutFinisher.finish()`'s own
     * save transaction purges any *uncompleted* set before rebuilding PRs, which would otherwise
     * silently delete this workout's one and only set.
     */
    suspend fun finishTracking(): FinishedTrack? {
        val startState = _state.value
        val workoutId = startState.workoutId ?: return null
        val workoutSetId = startState.workoutSetId ?: return null
        fixJob?.cancelAndJoin()

        // Re-read AFTER the join, not the pre-join snapshot above: a fix already in flight when
        // finishTracking() was called can still land in onFix() during cancelAndJoin()'s wait,
        // updating _state — using the stale startState here would silently drop that last fix's
        // distance contribution from the saved total.
        val durationSeconds = elapsedSeconds()
        val distanceMeters = _state.value.distanceMeters
        val now = clock.now().toEpochMilliseconds()
        workoutRepository.updateWorkoutSetDistance(workoutSetId, distanceMeters)
        workoutRepository.updateWorkoutSetDuration(workoutSetId, durationSeconds)
        workoutRepository.updateWorkoutSetCompletion(workoutSetId, true, now)
        activityTrackRepository.upsert(
            ActivityTrackEntity(
                id = UUID.randomUUID().toString(),
                workoutSetId = workoutSetId,
                routePolyline = routePoints.takeIf { it.isNotEmpty() }?.let(PolylineEncoding::encode),
                pointCount = routePoints.size,
                avgAccuracyM = routePoints.takeIf { it.isNotEmpty() }?.let { accuracySumMeters / it.size },
                // The time each point was recorded, so the summary can rebuild splits and a pace
                // chart after the fact (2026-09-26). distanceHistory alone can't: it is sampled
                // every 15 s at most and lives only in memory.
                routeTimes = routeTimes.takeIf { it.isNotEmpty() }?.let(PolylineEncoding::encodeDeltas),
            ),
        )
        workoutRepository.getById(workoutId)?.let { workout ->
            workoutRepository.updateWorkout(workout.copy(durationSeconds = durationSeconds, updatedAt = now))
        }
        _state.value = ActivityTrackingState()
        return FinishedTrack(workoutId, distanceMeters, durationSeconds)
    }

    /** Discards the in-progress track entirely — the caller is responsible for discarding the underlying `Workout` row too. */
    fun cancelTracking() {
        fixJob?.cancel()
        _state.value = ActivityTrackingState()
    }

    private companion object {
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 20f
        const val MIN_MOVEMENT_METERS = 3.0
        const val DISTANCE_HISTORY_INTERVAL_SECONDS = 15
    }
}
