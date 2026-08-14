package com.vcebricks.revision.data

import androidx.room.withTransaction
import com.vcebricks.revision.domain.ReviewOutcome
import com.vcebricks.revision.domain.ReviewScheduler
import com.vcebricks.revision.domain.capReviewAtTestDate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class RevisionRepository(
    private val database: RevisionDatabase,
    private val scheduler: ReviewScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = database.revisionDao()

    fun observeActiveTopics(): Flow<List<RevisionTopicEntity>> = dao.observeActiveTopics()
    fun observeAllTopics(): Flow<List<RevisionTopicEntity>> = dao.observeAllTopics()
    fun observeAttempts(topicId: Long): Flow<List<ReviewAttemptEntity>> = dao.observeAttempts(topicId)

    suspend fun addTopic(
        subject: String,
        topic: String,
        note: String,
        studyDate: LocalDate,
        testDate: LocalDate? = null,
    ): Long {
        val cleanSubject = subject.trim()
        val cleanTopic = topic.trim()
        require(cleanSubject.isNotEmpty())
        require(cleanTopic.isNotEmpty())
        require(testDate == null || !testDate.isBefore(studyDate))
        val now = Instant.now(clock).toEpochMilli()
        val firstReview = scheduler.firstReviewDate(studyDate)
        return dao.insertTopic(
            RevisionTopicEntity(
                subject = cleanSubject,
                topic = cleanTopic,
                note = note.trim(),
                studyDateEpochDay = studyDate.toEpochDay(),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                stageIndex = 0,
                nextReviewDateEpochDay = capReviewAtTestDate(firstReview, testDate, studyDate).toEpochDay(),
                testDateEpochDay = testDate?.toEpochDay(),
            ),
        )
    }

    suspend fun updateTopic(
        id: Long,
        subject: String,
        topic: String,
        note: String,
        testDate: LocalDate? = null,
    ) {
        val cleanSubject = subject.trim()
        val cleanTopic = topic.trim()
        require(cleanSubject.isNotEmpty())
        require(cleanTopic.isNotEmpty())
        val existing = dao.getTopic(id) ?: return
        val studyDate = LocalDate.ofEpochDay(existing.studyDateEpochDay)
        require(testDate == null || !testDate.isBefore(studyDate))
        val currentNext = LocalDate.ofEpochDay(existing.nextReviewDateEpochDay)
        val adjustedNext = if (existing.testCompletedAtEpochMillis != null) {
            currentNext
        } else {
            capReviewAtTestDate(currentNext, testDate, LocalDate.now(clock))
        }
        dao.updateTopic(
            existing.copy(
                subject = cleanSubject,
                topic = cleanTopic,
                note = note.trim(),
                testDateEpochDay = testDate?.toEpochDay(),
                nextReviewDateEpochDay = adjustedNext.toEpochDay(),
                updatedAtEpochMillis = Instant.now(clock).toEpochMilli(),
            ),
        )
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val existing = dao.getTopic(id) ?: return
        val today = LocalDate.now(clock)
        dao.updateTopic(
            existing.copy(
                isArchived = archived,
                nextReviewDateEpochDay = if (!archived && existing.nextReviewDateEpochDay < today.toEpochDay()) {
                    capReviewAtTestDate(today.plusDays(1), existing.testDateEpochDay?.let(LocalDate::ofEpochDay), today).toEpochDay()
                } else {
                    existing.nextReviewDateEpochDay
                },
                updatedAtEpochMillis = Instant.now(clock).toEpochMilli(),
            ),
        )
    }

    suspend fun setTestCompleted(id: Long, completed: Boolean) {
        val existing = dao.getTopic(id) ?: return
        val now = Instant.now(clock).toEpochMilli()
        val today = LocalDate.now(clock)
        dao.updateTopic(
            existing.copy(
                testCompletedAtEpochMillis = if (completed) now else null,
                nextReviewDateEpochDay = if (!completed && existing.nextReviewDateEpochDay < today.toEpochDay()) {
                    today.toEpochDay()
                } else {
                    existing.nextReviewDateEpochDay
                },
                updatedAtEpochMillis = now,
            ),
        )
    }

    suspend fun deleteTopic(id: Long) {
        val existing = dao.getTopic(id) ?: return
        database.withTransaction { dao.deleteTopic(existing) }
    }

    suspend fun completeReview(id: Long, outcome: ReviewOutcome, completionDate: LocalDate): Boolean =
        database.withTransaction {
            val existing = dao.getTopic(id) ?: return@withTransaction false
            if (existing.isArchived || existing.testCompletedAtEpochMillis != null) return@withTransaction false
            val result = scheduler.nextReview(existing.stageIndex, outcome, completionDate)
            val testDate = existing.testDateEpochDay?.let(LocalDate::ofEpochDay)
            val nextReview = capReviewAtTestDate(result.nextReviewDate, testDate, completionDate)
            val completedAt = Instant.now(clock).toEpochMilli()
            val attempt = ReviewAttemptEntity(
                topicId = id,
                scheduledDueDateEpochDay = existing.nextReviewDateEpochDay,
                completedAtEpochMillis = completedAt,
                completedLocalDateEpochDay = completionDate.toEpochDay(),
                outcome = outcome,
                previousStageIndex = existing.stageIndex,
                newStageIndex = result.newStageIndex,
                calculatedNextDueDateEpochDay = nextReview.toEpochDay(),
                wasEarly = completionDate.toEpochDay() < existing.nextReviewDateEpochDay,
            )
            val inserted = dao.insertAttempt(attempt)
            if (inserted == -1L) return@withTransaction false
            dao.updateTopic(
                existing.copy(
                    stageIndex = result.newStageIndex,
                    nextReviewDateEpochDay = nextReview.toEpochDay(),
                    lastReviewedAtEpochMillis = completedAt,
                    updatedAtEpochMillis = completedAt,
                ),
            )
            true
        }

    suspend fun getDueTopics(today: LocalDate): List<RevisionTopicEntity> = dao.getDueTopics(today.toEpochDay())

    suspend fun getActiveTopicsSnapshot(): List<RevisionTopicEntity> = dao.getActiveTopicsSnapshot()
}
