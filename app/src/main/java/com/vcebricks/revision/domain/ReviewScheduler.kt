package com.vcebricks.revision.domain

import java.time.LocalDate
import kotlin.math.roundToInt

enum class ReviewOutcome {
    FORGOT,
    PARTLY_RECALLED,
    RECALLED_WELL,
}

data class ScheduleResult(
    val newStageIndex: Int,
    val nextReviewDate: LocalDate,
)

class ReviewScheduler(
    private val intervalsDays: List<Int> = listOf(1, 3, 7, 14, 30, 60, 120),
) {
    init {
        require(intervalsDays.isNotEmpty())
        require(intervalsDays.all { it > 0 })
    }

    val finalStageIndex: Int = intervalsDays.lastIndex

    fun firstReviewDate(studyDate: LocalDate): LocalDate = studyDate.plusDays(intervalsDays.first().toLong())

    fun nextReview(
        currentStageIndex: Int,
        outcome: ReviewOutcome,
        actualCompletionDate: LocalDate,
    ): ScheduleResult {
        val safeStage = currentStageIndex.coerceIn(0, finalStageIndex)
        return when (outcome) {
            ReviewOutcome.FORGOT -> ScheduleResult(
                newStageIndex = (safeStage - 1).coerceAtLeast(0),
                nextReviewDate = actualCompletionDate.plusDays(1),
            )

            ReviewOutcome.PARTLY_RECALLED -> {
                // At stage 0, a partial recall should not wait longer than the original one-day interval.
                val shortened = if (safeStage == 0) {
                    1
                } else {
                    (intervalsDays[safeStage] * 0.6).roundToInt().coerceAtLeast(2)
                }
                ScheduleResult(
                    newStageIndex = safeStage,
                    nextReviewDate = actualCompletionDate.plusDays(shortened.toLong()),
                )
            }

            ReviewOutcome.RECALLED_WELL -> {
                val newStage = (safeStage + 1).coerceAtMost(finalStageIndex)
                ScheduleResult(
                    newStageIndex = newStage,
                    nextReviewDate = actualCompletionDate.plusDays(intervalsDays[newStage].toLong()),
                )
            }
        }
    }
}
