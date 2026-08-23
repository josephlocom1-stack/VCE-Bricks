package com.vcebricks.revision.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val MIN_VISIBLE_UPCOMING_PROGRESS = 0.08f

data class ReviewProgress(
    val fraction: Float,
    val statusText: String,
)

fun calculateReviewProgress(
    intervalStartDate: LocalDate,
    dueDate: LocalDate,
    today: LocalDate,
): ReviewProgress {
    val totalDays = ChronoUnit.DAYS.between(intervalStartDate, dueDate).coerceAtLeast(1)
    val elapsedDays = ChronoUnit.DAYS.between(intervalStartDate, today).coerceIn(0, totalDays)
    val rawFraction = when {
        !today.isBefore(dueDate) -> 1f
        !today.isAfter(intervalStartDate) -> 0f
        else -> (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    }

    // A completely empty fill looked like no progress component at all on some screens.
    // Keep a small display-only segment visible for upcoming reviews. The exact timing is
    // still communicated by statusText, while due and overdue reviews remain fully filled.
    val displayFraction = when {
        !today.isBefore(dueDate) -> 1f
        else -> rawFraction.coerceAtLeast(MIN_VISIBLE_UPCOMING_PROGRESS)
    }

    val daysRemaining = ChronoUnit.DAYS.between(today, dueDate)
    val status = when {
        daysRemaining > 1 -> "$daysRemaining days until review"
        daysRemaining == 1L -> "1 day until review"
        daysRemaining == 0L -> "Review due today"
        daysRemaining == -1L -> "1 day overdue"
        else -> "${-daysRemaining} days overdue"
    }

    return ReviewProgress(fraction = displayFraction, statusText = status)
}
