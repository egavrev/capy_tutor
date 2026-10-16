package com.capytutor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lesson ORDER BY subject, createdAt")
    fun observeAll(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lesson ORDER BY createdAt, id")
    suspend fun all(): List<LessonEntity>

    @Query("SELECT * FROM lesson WHERE id = :id")
    suspend fun get(id: Long): LessonEntity?

    @Query("SELECT * FROM lesson WHERE id = :id")
    fun observe(id: Long): Flow<LessonEntity?>

    @Query("SELECT * FROM lesson WHERE mdHash = :hash LIMIT 1")
    suspend fun byHash(hash: String): LessonEntity?

    @Insert
    suspend fun insert(lesson: LessonEntity): Long

    @Update
    suspend fun update(lesson: LessonEntity)

    @Query("DELETE FROM lesson WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM key_idea WHERE lessonId = :lessonId ORDER BY ord")
    suspend fun ideas(lessonId: Long): List<KeyIdeaEntity>

    @Query("SELECT * FROM key_idea WHERE lessonId = :lessonId ORDER BY ord")
    fun observeIdeas(lessonId: Long): Flow<List<KeyIdeaEntity>>

    @Insert
    suspend fun insertIdeas(ideas: List<KeyIdeaEntity>)

    @Update
    suspend fun updateIdea(idea: KeyIdeaEntity)

    @Query("DELETE FROM key_idea WHERE lessonId = :lessonId")
    suspend fun deleteIdeas(lessonId: Long)

    @Transaction
    suspend fun replaceIdeas(lessonId: Long, ideas: List<KeyIdeaEntity>) {
        deleteIdeas(lessonId)
        insertIdeas(ideas)
    }
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE lessonId = :lessonId AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openFor(lessonId: Long): SessionEntity?

    @Query("SELECT lessonId FROM session WHERE endedAt IS NULL")
    fun observeOpenLessonIds(): Flow<List<Long>>

    @Query("SELECT * FROM session WHERE lessonId = :lessonId ORDER BY startedAt DESC")
    fun observeFor(lessonId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT COUNT(*) FROM turn WHERE sessionId = :sessionId")
    suspend fun turnCount(sessionId: Long): Int

    @Insert
    suspend fun insertTurns(turns: List<TurnEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResults(results: List<IdeaResultEntity>)

    @Query("SELECT * FROM idea_result WHERE sessionId = :sessionId")
    fun observeResults(sessionId: Long): Flow<List<IdeaResultEntity>>

    @Query("SELECT * FROM idea_result WHERE sessionId = :sessionId")
    suspend fun results(sessionId: Long): List<IdeaResultEntity>

    /** Sessions that reached the summary (E15: progress). */
    @Query("SELECT * FROM session WHERE phase IN ('SUMMARY', 'DONE') ORDER BY startedAt")
    suspend fun finished(): List<SessionEntity>

    @Query("SELECT * FROM idea_result WHERE sessionId IN (:sessionIds)")
    suspend fun resultsFor(sessionIds: List<Long>): List<IdeaResultEntity>

    @Query("UPDATE idea_result SET parentDecision = :decision WHERE sessionId = :sessionId AND ideaKey = :ideaKey")
    suspend fun decide(sessionId: Long, ideaKey: String, decision: String)
}

/** Totals per call type for one day. */
data class UsageTotal(
    val callType: String,
    val requests: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val audioSeconds: Int,
    val rateLimitedCount: Int,
)

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(row: ProviderUsageEntity)

    @Query(
        "SELECT callType, SUM(requests) AS requests, SUM(inputTokens) AS inputTokens, SUM(outputTokens) AS outputTokens, " +
            "SUM(audioSeconds) AS audioSeconds, SUM(rateLimitedCount) AS rateLimitedCount " +
            "FROM provider_usage WHERE date = :date GROUP BY callType ORDER BY callType",
    )
    fun observeDay(date: String): Flow<List<UsageTotal>>
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM quiz WHERE lessonId = :lessonId")
    suspend fun get(lessonId: Long): QuizEntity?

    @Query("SELECT * FROM quiz WHERE lessonId = :lessonId")
    fun observe(lessonId: Long): Flow<QuizEntity?>

    @Query("SELECT lessonId FROM quiz")
    fun observeLessonIds(): Flow<List<Long>>

    @Query("SELECT lessonId FROM quiz")
    suspend fun lessonIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quiz: QuizEntity)

    @Query("DELETE FROM quiz WHERE lessonId = :lessonId")
    suspend fun delete(lessonId: Long)

    @Query("SELECT * FROM quiz_attempt WHERE lessonId = :lessonId AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openAttempt(lessonId: Long): QuizAttemptEntity?

    @Query("SELECT * FROM quiz_attempt WHERE finishedAt IS NOT NULL ORDER BY finishedAt DESC")
    fun observeFinished(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempt WHERE lessonId = :lessonId AND finishedAt IS NOT NULL ORDER BY finishedAt DESC")
    fun observeFinishedFor(lessonId: Long): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempt WHERE finishedAt IS NOT NULL ORDER BY finishedAt")
    suspend fun finishedAttempts(): List<QuizAttemptEntity>

    @Query("SELECT * FROM quiz_attempt WHERE id = :id")
    suspend fun attempt(id: Long): QuizAttemptEntity?

    @Insert
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Update
    suspend fun updateAttempt(attempt: QuizAttemptEntity)

    /** A new quiz replaces the old one; unfinished attempts of the old one cannot continue. */
    @Query("DELETE FROM quiz_attempt WHERE lessonId = :lessonId AND finishedAt IS NULL")
    suspend fun dropOpenAttempts(lessonId: Long)
}

@Dao
interface BowDao {
    /** Ignored when the session already has its bows (one award per session). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(award: BowAwardEntity): Long

    @Query("SELECT * FROM bow_award ORDER BY at")
    fun observeAll(): Flow<List<BowAwardEntity>>

    @Query("SELECT MAX(bows) FROM bow_award WHERE lessonId = :lessonId")
    suspend fun best(lessonId: Long): Int?

    @Query("SELECT * FROM bow_award WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: Long): BowAwardEntity?
}
