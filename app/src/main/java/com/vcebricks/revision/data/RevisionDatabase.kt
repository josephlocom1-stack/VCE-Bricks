package com.vcebricks.revision.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.vcebricks.revision.domain.ReviewOutcome
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "revision_topics",
    indices = [
        Index("nextReviewDateEpochDay"),
        Index("isArchived"),
    ],
)
data class RevisionTopicEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val topic: String,
    val note: String,
    val studyDateEpochDay: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val stageIndex: Int,
    val nextReviewDateEpochDay: Long,
    val lastReviewedAtEpochMillis: Long? = null,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "review_attempts",
    foreignKeys = [
        ForeignKey(
            entity = RevisionTopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("topicId"),
        Index(
            value = ["topicId", "scheduledDueDateEpochDay", "completedLocalDateEpochDay", "previousStageIndex"],
            unique = true,
        ),
    ],
)
data class ReviewAttemptEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long,
    val scheduledDueDateEpochDay: Long,
    val completedAtEpochMillis: Long,
    val completedLocalDateEpochDay: Long,
    val outcome: ReviewOutcome,
    val previousStageIndex: Int,
    val newStageIndex: Int,
    val calculatedNextDueDateEpochDay: Long,
    val wasEarly: Boolean,
)

class DatabaseConverters {
    @TypeConverter
    fun outcomeToString(value: ReviewOutcome): String = value.name

    @TypeConverter
    fun stringToOutcome(value: String): ReviewOutcome = ReviewOutcome.valueOf(value)
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM revision_topics WHERE isArchived = 0 ORDER BY nextReviewDateEpochDay ASC, subject COLLATE NOCASE ASC, topic COLLATE NOCASE ASC")
    fun observeActiveTopics(): Flow<List<RevisionTopicEntity>>

    @Query("SELECT * FROM revision_topics ORDER BY isArchived ASC, nextReviewDateEpochDay ASC, subject COLLATE NOCASE ASC, topic COLLATE NOCASE ASC")
    fun observeAllTopics(): Flow<List<RevisionTopicEntity>>

    @Query("SELECT * FROM revision_topics WHERE id = :id LIMIT 1")
    suspend fun getTopic(id: Long): RevisionTopicEntity?

    @Query("SELECT * FROM revision_topics WHERE isArchived = 0 AND nextReviewDateEpochDay <= :todayEpochDay ORDER BY nextReviewDateEpochDay ASC, subject COLLATE NOCASE ASC")
    suspend fun getDueTopics(todayEpochDay: Long): List<RevisionTopicEntity>

    @Query("SELECT * FROM review_attempts WHERE topicId = :topicId ORDER BY completedAtEpochMillis DESC")
    fun observeAttempts(topicId: Long): Flow<List<ReviewAttemptEntity>>

    @Insert
    suspend fun insertTopic(topic: RevisionTopicEntity): Long

    @Update
    suspend fun updateTopic(topic: RevisionTopicEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempt(attempt: ReviewAttemptEntity): Long

    @Delete
    suspend fun deleteTopic(topic: RevisionTopicEntity)
}

@Database(
    entities = [RevisionTopicEntity::class, ReviewAttemptEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class RevisionDatabase : RoomDatabase() {
    abstract fun revisionDao(): RevisionDao
}
