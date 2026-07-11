package com.vcebricks.revision.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val fraction = when {
        !today.isBefore(dueDate) -> 1f
        !today.isAfter(intervalStartDate) -> 0f
        else -> (elapsedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
    }
    val daysRemaining = ChronoUnit.DAYS.between(today, dueDate)

    val status = when {
        daysRemaining > 1 -> "$daysRemaining days until review"
        daysRemaining == 1L -> "1 day until review"
        daysRemaining == 0L -> "Review due today"
        daysRemaining == -1L -> "1 day overdue"
        else -> "${-daysRemaining} days overdue"
    }

    return ReviewProgress(fraction = fraction, statusText = status)
}
