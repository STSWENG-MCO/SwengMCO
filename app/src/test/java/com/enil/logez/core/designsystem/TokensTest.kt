package com.enil.logez.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the Spacing scale against literal values (Owner testing rule, PHASE2_PLAN.md §10.1
 * rule 1) so a future edit to Tokens.kt that silently breaks the ~25%+ step growth fails a test
 * instead of shipping unnoticed.
 */
class TokensTest {

    @Test
    fun `spacing scale matches the design system's declared step values`() {
        assertEquals(4.dp, Spacing.xxs)
        assertEquals(8.dp, Spacing.xs)
        assertEquals(12.dp, Spacing.sm)
        assertEquals(16.dp, Spacing.md)
        assertEquals(24.dp, Spacing.lg)
        assertEquals(32.dp, Spacing.xl)
        assertEquals(48.dp, Spacing.xxl)
        assertEquals(64.dp, Spacing.xxxl)
    }

    @Test
    fun `each spacing step is at least 25 percent larger than the previous step`() {
        val scale = listOf(
            Spacing.xxs, Spacing.xs, Spacing.sm, Spacing.md,
            Spacing.lg, Spacing.xl, Spacing.xxl, Spacing.xxxl,
        )
        for (i in 1 until scale.size) {
            val growth = scale[i].value / scale[i - 1].value
            assert(growth >= 1.25f) {
                "Step ${scale[i - 1]} -> ${scale[i]} only grew by ${growth}x, expected >= 1.25x"
            }
        }
    }
}
