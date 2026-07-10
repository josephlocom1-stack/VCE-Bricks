package com.vcebricks.revision.data

import androidx.room.withTransaction
import com.vcebricks.revision.domain.ReviewOutcome
import com.vcebricks.revision.domain.ReviewScheduler
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

    suspend fun addTopic(subject: String, topic: String, note: String, studyDate: LocalDate): Long {
        val cleanSubject = subject.trim()
        val cleanTopic = topic.trim()
        require(cleanSubject.isNotEmpty())
        require(cleanTopic.isNotEmpty())
        val now = Instant.now(clock).toEpochMilli()
        return dao.insertTopic(
            RevisionTopicEntity(
                subject = cleanSubject,
                topic = cleanTopic,
                note = note.trim(),
                studyDateEpochDay = studyDate.toEpochDay(),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                stageIndex = 0,
                nextReviewDateEpochDay = scheduler.firstReviewDate(studyDate).toEpochDay(),
            ),
        )
    }

    suspend fun updateTopic(id: Long, subject: String, topic: String, note: String) {
        val cleanSubject = subject.trim()
        val cleanTopic = topic.trim()
        require(cleanSubject.isNotEmpty())
        require(cleanTopic.isNotEmpty())
        val existing = dao.getTopic(id) ?: return
        dao.updateTopic(
            existing.copy(
                subject = cleanSubject,
                topic = cleanTopic,
                note = note.trim(),
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
                    today.plusDays(1).toEpochDay()
                } else {
                    existing.nextReviewDateEpochDay
                },
                updatedAtEpochMillis = Instant.now(clock).toEpochMilli(),
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
            if (existing.isArchived) return@withTransaction false
            val result = scheduler.nextReview(existing.stageIndex, outcome, completionDate)
            val completedAt = Instant.now(clock).toEpochMilli()
            val attempt = ReviewAttemptEntity(
                topicId = id,
                scheduledDueDateEpochDay = existing.nextReviewDateEpochDay,
                completedAtEpochMillis = completedAt,
                completedLocalDateEpochDay = completionDate.toEpochDay(),
                outcome = outcome,
                previousStageIndex = existing.stageIndex,
                newStageIndex = result.newStageIndex,
                calculatedNextDueDateEpochDay = result.nextReviewDate.toEpochDay(),
                wasEarly = completionDate.toEpochDay() < existing.nextReviewDateEpochDay,
            )
            val inserted = dao.insertAttempt(attempt)
            if (inserted == -1L) return@withTransaction false
            dao.updateTopic(
                existing.copy(
                    stageIndex = result.newStageIndex,
                    nextReviewDateEpochDay = result.nextReviewDate.toEpochDay(),
                    lastReviewedAtEpochMillis = completedAt,
                    updatedAtEpochMillis = completedAt,
                ),
            )
            true
        }

    suspend fun getDueTopics(today: LocalDate): List<RevisionTopicEntity> = dao.getDueTopics(today.toEpochDay())
}
