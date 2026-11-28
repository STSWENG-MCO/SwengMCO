package com.enil.logez.feature.workout.finish.share

import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.ShareBackground
import com.enil.logez.core.domain.model.ShareMetric
import com.enil.logez.core.domain.model.ShareTemplate
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.ShareTemplateRegistry
import com.enil.logez.core.domain.model.ShareTextTone
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardContentTest {
    private val run =
        GpsShareCardData(
            eyebrow = "Run complete",
            routePoints = emptyList(),
            distanceNumber = "4.79",
            distanceUnitLabel = "km",
            timeText = "29:21",
            paceText = "6:07",
            paceLabel = "Pace /km",
            avgBpmText = "150",
            prLines = listOf("Fastest km" to "5:46"),
        )

    private fun data(
        volume: String? = "4,382 kg",
        reps: String? = "117",
        gps: GpsShareCardData? = null,
        choice: ShareTemplateChoice? = null,
    ) = ShareCardData(
        title = "Push Day",
        dateLine = "Fri 9 Oct 2026",
        durationText = "52 min",
        volumeText = volume,
        setsText = "13",
        repsText = reps,
        muscleIntensity = emptyMap(),
        muscleBalance = emptyList(),
        prs = emptyList(),
        exerciseLines = emptyList(),
        structure = WorkoutStructure.REGULAR,
        rounds = 0,
        gps = gps,
        choice = choice,
    )

    private fun choiceWith(
        template: ShareTemplate,
        kind: WorkoutKind,
        metrics: Set<ShareMetric>? = null,
        numberMetric: ShareMetric? = null,
    ): ShareTemplateChoice {
        val base = ShareTemplateRegistry.defaultChoice(kind)
        return base.copy(
            template = template,
            customization =
                base.customization.copy(
                    metrics = metrics ?: base.customization.metrics,
                    numberMetric = numberMetric ?: base.customization.numberMetric,
                ),
        )
    }

    @Test
    fun `value and unit are split at the first letter`() {
        assertEquals("4,382" to "kg", splitValueUnit("4,382 kg"))
        assertEquals("4382" to "lb", splitValueUnit("4382lb"))
        assertEquals("29:21" to null, splitValueUnit("29:21"))
    }

    @Test
    fun `a strength workout lists volume time sets and reps in the mockup order`() {
        val metrics = data().availableStats().map { it.metric }
        assertEquals(listOf(ShareMetric.VOLUME, ShareMetric.TIME, ShareMetric.SETS, ShareMetric.REPS), metrics)
        assertEquals(ShareStat(ShareMetric.VOLUME, "4,382", "kg"), data().availableStats().first())
    }

    @Test
    fun `a figure the workout never logged is absent not zero`() {
        val metrics = data(volume = null, reps = null).availableStats().map { it.metric }
        assertEquals(listOf(ShareMetric.TIME, ShareMetric.SETS), metrics)
    }

    @Test
    fun `a run lists distance time pace and average bpm`() {
        val stats = data(gps = run).availableStats()
        assertEquals(
            listOf(ShareMetric.DISTANCE, ShareMetric.TIME, ShareMetric.PACE, ShareMetric.AVG_BPM),
            stats.map { it.metric },
        )
        assertEquals(ShareStat(ShareMetric.DISTANCE, "4.79", "km"), stats.first())
    }

    @Test
    fun `a run without a watch has no average bpm`() {
        val metrics = data(gps = run.copy(avgBpmText = null)).availableStats().map { it.metric }
        assertFalse(ShareMetric.AVG_BPM in metrics)
    }

    @Test
    fun `shown stats follow the Show chips`() {
        val choice = choiceWith(ShareTemplate.STACK, WorkoutKind.STRENGTH, metrics = setOf(ShareMetric.VOLUME, ShareMetric.SETS))
        assertEquals(listOf(ShareMetric.VOLUME, ShareMetric.SETS), data().shownStats(choice).map { it.metric })
    }

    @Test
    fun `number stat is the chosen metric or the first one logged`() {
        val sets = choiceWith(ShareTemplate.NUMBER, WorkoutKind.STRENGTH, numberMetric = ShareMetric.SETS)
        assertEquals(ShareMetric.SETS, data().numberStat(sets)?.metric)

        val distanceOnStrength = choiceWith(ShareTemplate.NUMBER, WorkoutKind.STRENGTH, numberMetric = ShareMetric.DISTANCE)
        assertEquals(ShareMetric.VOLUME, data().numberStat(distanceOnStrength)?.metric)
    }

    @Test
    fun `record count is trophies for strength and record lines for a run`() {
        assertEquals(0, data().recordCount())
        assertEquals(1, data(gps = run).recordCount())
    }

    @Test
    fun `plain grounds fix their text colour and only clear uses the choice`() {
        assertTrue(inkIsLight(ShareBackground.DARK, ShareTextTone.DARK))
        assertFalse(inkIsLight(ShareBackground.LIME, ShareTextTone.LIGHT))
        assertFalse(inkIsLight(ShareBackground.LIGHT, ShareTextTone.LIGHT))
        assertTrue(inkIsLight(ShareBackground.CLEAR, ShareTextTone.LIGHT))
        assertFalse(inkIsLight(ShareBackground.CLEAR, ShareTextTone.DARK))
    }

    @Test
    fun `no saved choice resolves to the default template`() {
        assertEquals(ShareTemplate.STACK, data().resolvedChoice().template)
        assertEquals(ShareTemplate.STACK, data(gps = run).resolvedChoice().template)
    }

    @Test
    fun `a saved rendered template is kept`() {
        val glass = choiceWith(ShareTemplate.GLASS, WorkoutKind.STRENGTH)
        assertEquals(ShareTemplate.GLASS, data(choice = glass).resolvedChoice().template)
    }

    @Test
    fun `a saved template this workout cannot use falls back to the default`() {
        val route = choiceWith(ShareTemplate.ROUTE, WorkoutKind.GPS_TRACKED)
        assertEquals(ShareTemplate.STACK, data(choice = route).resolvedChoice().template)

        val records = choiceWith(ShareTemplate.RECORDS, WorkoutKind.STRENGTH)
        assertEquals(ShareTemplate.STACK, data(choice = records).resolvedChoice().template)
    }

    @Test
    fun `selectable templates never include one the kind does not support`() {
        val strength = data().selectableTemplates()
        assertTrue(strength.all { WorkoutKind.STRENGTH in it.kinds })

        assertTrue(ShareTemplate.STACK in strength)
        assertFalse(ShareTemplate.ROUTE in strength)
    }

    @Test
    fun `records template needs a record`() {
        assertFalse(ShareTemplate.RECORDS.isAvailableFor(data()))
        assertTrue(ShareTemplate.RECORDS.isAvailableFor(data(gps = run)))
    }

    @Test
    fun `route template needs a drawn route`() {
        assertFalse(ShareTemplate.ROUTE.isAvailableFor(data(gps = run)))
        val withRoute = run.copy(routePoints = listOf(14.0 to 121.0, 14.001 to 121.001))
        assertTrue(ShareTemplate.ROUTE.isAvailableFor(data(gps = withRoute)))
        assertNull(data().gps)
    }

    @Test
    fun `splits template needs at least two splits`() {
        assertFalse(ShareTemplate.SPLITS.isAvailableFor(data(gps = run)))
        val splits = listOf(ShareSplit(1, 366.0, "6:06"), ShareSplit(2, 346.0, "5:46"))
        assertTrue(ShareTemplate.SPLITS.isAvailableFor(data(gps = run.copy(splits = splits))))
    }

    @Test
    fun `streak template needs streak figures`() {
        assertFalse(ShareTemplate.STREAK.isAvailableFor(data()))
        val streak = ShareStreak(9, 3, 4, List(12) { 4 }, 8, "October")
        assertTrue(ShareTemplate.STREAK.isAvailableFor(data().copy(streak = streak)))
    }

    @Test
    fun `summary and log need exercises and muscle templates need mappable muscles`() {
        assertFalse(ShareTemplate.SUMMARY.isAvailableFor(data()))
        assertFalse(ShareTemplate.BODY_MAP.isAvailableFor(data()))
        val withExercise = data().copy(exercises = listOf(ShareExercise("Bench Press", "72.5 × 8", null, emptyList(), false)))
        assertTrue(ShareTemplate.SUMMARY.isAvailableFor(withExercise))
        assertTrue(ShareTemplate.LOG.isAvailableFor(withExercise))
        val withMuscle = data().copy(muscleIntensity = mapOf(MuscleGroup.CHEST to 1f))
        assertTrue(ShareTemplate.BODY_MAP.isAvailableFor(withMuscle))
        assertTrue(ShareTemplate.MUSCLES.isAvailableFor(withMuscle))
    }
}
