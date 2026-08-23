package com.vcebricks.revision.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulerTest {
    private val scheduler = ReviewScheduler()
    private val completion = LocalDate.of(2026, 7, 15)

    @Test
    fun firstReview_isOneDayAfterStudy() {
        assertEquals(LocalDate.of(2026, 7, 11), scheduler.firstReviewDate(LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun forgot_movesBackAndSchedulesTomorrow() {
        val result = scheduler.nextReview(3, ReviewOutcome.FORGOT, completion)
        assertEquals(2, result.newStageIndex)
        assertEquals(LocalDate.of(2026, 7, 16), result.nextReviewDate)
    }

    @Test
    fun forgot_neverMovesBelowZero() {
        assertEquals(0, scheduler.nextReview(0, ReviewOutcome.FORGOT, completion).newStageIndex)
    }

    @Test
    fun partlyRecalled_keepsStageAndUsesSixtyPercentRounded() {
        val result = scheduler.nextReview(3, ReviewOutcome.PARTLY_RECALLED, completion)
        assertEquals(3, result.newStageIndex)
        assertEquals(LocalDate.of(2026, 7, 23), result.nextReviewDate)
    }

    @Test
    fun partlyRecalledAtFirstStage_returnsTomorrowRatherThanWaitingLonger() {
        val result = scheduler.nextReview(0, ReviewOutcome.PARTLY_RECALLED, completion)
        assertEquals(0, result.newStageIndex)
        assertEquals(completion.plusDays(1), result.nextReviewDate)
    }

    @Test
    fun recalledWell_advancesAndUsesNewStageInterval() {
        val result = scheduler.nextReview(0, ReviewOutcome.RECALLED_WELL, completion)
        assertEquals(1, result.newStageIndex)
        assertEquals(LocalDate.of(2026, 7, 18), result.nextReviewDate)
    }

    @Test
    fun finalStage_isCappedAndContinuesSchedulingEveryOneHundredTwentyDays() {
        val first = scheduler.nextReview(6, ReviewOutcome.RECALLED_WELL, completion)
        val second = scheduler.nextReview(6, ReviewOutcome.RECALLED_WELL, first.nextReviewDate)

        assertEquals(6, first.newStageIndex)
        assertEquals(completion.plusDays(120), first.nextReviewDate)
        assertEquals(first.nextReviewDate.plusDays(120), second.nextReviewDate)
    }

    @Test
    fun lateReview_isCalculatedFromActualCompletionDate() {
        val result = scheduler.nextReview(1, ReviewOutcome.RECALLED_WELL, LocalDate.of(2026, 7, 20))
        assertEquals(LocalDate.of(2026, 7, 27), result.nextReviewDate)
    }

    @Test
    fun invalidStage_isSafelyClamped() {
        assertEquals(6, scheduler.nextReview(99, ReviewOutcome.RECALLED_WELL, completion).newStageIndex)
    }
}
