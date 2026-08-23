package com.vcebricks.revision.domain

import java.time.LocalDate

fun capReviewAtTestDate(
    proposedDate: LocalDate,
    testDate: LocalDate?,
    floorDate: LocalDate,
): LocalDate = when {
    testDate == null -> proposedDate
    testDate.isBefore(floorDate) -> floorDate
    proposedDate.isAfter(testDate) -> testDate
    else -> proposedDate
}
