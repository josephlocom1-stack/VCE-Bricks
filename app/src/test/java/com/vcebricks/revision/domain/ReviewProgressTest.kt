package com.vcebricks.revision.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewProgressTest {
    private val start = LocalDate.of(2026, 7, 1)
    private val due = LocalDate.of(2026, 7, 11)

    @Test
    fun progress_hasVisibleFillAtIntervalStart() {
        val result = calculateReviewProgress(start, due, start)
        assertTrue(result.fraction > 0f)
        assertEquals(0.08f, result.fraction)
        assertEquals("10 days until review", result.statusText)
    }

    @Test
    fun progress_isHalfwayAtMidpoint() {
        val result = calculateReviewProgress(start, due, LocalDate.of(2026, 7, 6))
        assertEquals(0.5f, result.fraction)
        assertEquals("5 days until review", result.statusText)
    }

    @Test
    fun progress_isFullWhenDue() {
        val result = calculateReviewProgress(start, due, due)
        assertEquals(1f, result.fraction)
        assertEquals("Review due today", result.statusText)
    }

    @Test
    fun overdueProgress_staysClampedAtFull() {
        val result = calculateReviewProgress(start, due, due.plusDays(3))
        assertEquals(1f, result.fraction)
        assertEquals("3 days overdue", result.statusText)
    }

    @Test
    fun invalidOrZeroLengthInterval_doesNotDivideByZero() {
        val result = calculateReviewProgress(due, due, due)
        assertEquals(1f, result.fraction)
        assertEquals("Review due today", result.statusText)
    }
}
