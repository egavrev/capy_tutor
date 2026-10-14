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
