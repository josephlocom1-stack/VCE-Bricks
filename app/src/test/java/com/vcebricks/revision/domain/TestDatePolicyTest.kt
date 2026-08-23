package com.vcebricks.revision.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TestDatePolicyTest {
    private val today = LocalDate.of(2026, 7, 15)

    @Test
    fun noTestDate_keepsAdaptiveReviewDate() {
        assertEquals(today.plusDays(30), capReviewAtTestDate(today.plusDays(30), null, today))
    }

    @Test
    fun reviewAfterTest_isCappedAtTestDate() {
        val testDate = today.plusDays(10)
        assertEquals(testDate, capReviewAtTestDate(today.plusDays(30), testDate, today))
    }

    @Test
    fun reviewBeforeTest_isNotChanged() {
        val proposed = today.plusDays(3)
        assertEquals(proposed, capReviewAtTestDate(proposed, today.plusDays(10), today))
    }

    @Test
    fun passedTestDate_neverCreatesAnotherPastDueDate() {
        assertEquals(today, capReviewAtTestDate(today.plusDays(7), today.minusDays(1), today))
    }
}
