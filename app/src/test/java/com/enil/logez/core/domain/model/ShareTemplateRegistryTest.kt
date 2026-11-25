package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTemplateRegistryTest {
    @Test
    fun `registry holds exactly the 13 mockup templates in strip order`() {
        assertEquals(
            listOf(
                "STACK",
                "NUMBER",
                "RECORDS",
                "GLASS",
                "RAIL",
                "STRIP",
                "BODY_MAP",
                "ROUTE",
                "SPLITS",
                "SUMMARY",
                "MUSCLES",
                "LOG",
                "STREAK",
            ),
            ShareTemplateRegistry.all.map { it.name },
        )
    }

    @Test
    fun `routines offer 11 templates and outdoors offer 9`() {
        assertEquals(11, ShareTemplateRegistry.forKind(WorkoutKind.STRENGTH).size)
        assertEquals(9, ShareTemplateRegistry.forKind(WorkoutKind.GPS_TRACKED).size)
    }

    @Test
    fun `route and splits are outdoors only`() {
        listOf(ShareTemplate.ROUTE, ShareTemplate.SPLITS).forEach {
            assertEquals(setOf(WorkoutKind.GPS_TRACKED), it.kinds)
        }
    }

    @Test
    fun `body map summary muscles and log are routines only`() {
        listOf(ShareTemplate.BODY_MAP, ShareTemplate.SUMMARY, ShareTemplate.MUSCLES, ShareTemplate.LOG).forEach {
            assertEquals(setOf(WorkoutKind.STRENGTH), it.kinds)
        }
    }

    @Test
    fun `streak and the six shared layouts serve both kinds`() {
        listOf(
            ShareTemplate.STACK,
            ShareTemplate.NUMBER,
            ShareTemplate.RECORDS,
            ShareTemplate.GLASS,
            ShareTemplate.RAIL,
            ShareTemplate.STRIP,
            ShareTemplate.STREAK,
        ).forEach {
            assertEquals(WorkoutKind.entries.toSet(), it.kinds)
        }
    }

    @Test
    fun `only summary muscles log and streak are plain-colour only`() {
        val plainOnly = ShareTemplate.entries.filterNot { it.worksOnPhoto }
        assertEquals(
            setOf(ShareTemplate.SUMMARY, ShareTemplate.MUSCLES, ShareTemplate.LOG, ShareTemplate.STREAK),
            plainOnly.toSet(),
        )
    }

    @Test
    fun `default choice is stack on dark with light text for every kind`() {
        WorkoutKind.entries.forEach { kind ->
            val choice = ShareTemplateRegistry.defaultChoice(kind)
            assertEquals(ShareTemplate.STACK, choice.template)
            assertEquals(ShareBackground.DARK, choice.customization.background)
            assertEquals(ShareTextTone.LIGHT, choice.customization.textTone)
            assertTrue(ShareTemplateRegistry.supports(choice.template, kind))
        }
    }

    @Test
    fun `default show chips match each kind and keep start time off`() {
        val routines = ShareTemplateRegistry.defaultChoice(WorkoutKind.STRENGTH).customization
        assertEquals(setOf(ShareMetric.VOLUME, ShareMetric.TIME, ShareMetric.SETS, ShareMetric.REPS), routines.metrics)
        assertEquals(setOf(ShareDetail.DATE, ShareDetail.RECORDS, ShareDetail.EXERCISES, ShareDetail.LOGEZ_MARK), routines.details)
        assertEquals(ShareMetric.VOLUME, routines.numberMetric)

        val outdoors = ShareTemplateRegistry.defaultChoice(WorkoutKind.GPS_TRACKED).customization
        assertEquals(setOf(ShareMetric.DISTANCE, ShareMetric.TIME, ShareMetric.PACE, ShareMetric.AVG_BPM), outdoors.metrics)
        assertEquals(
            setOf(ShareDetail.DATE, ShareDetail.RECORDS, ShareDetail.LOGEZ_MARK, ShareDetail.HIDE_ROUTE_ENDS),
            outdoors.details,
        )
        assertEquals(ShareMetric.DISTANCE, outdoors.numberMetric)
        assertFalse(ShareDetail.START_TIME in routines.details)
        assertFalse(ShareDetail.START_TIME in outdoors.details)
    }
}
