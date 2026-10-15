package com.capytutor.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tables (HANDOFF.md §9). Text columns hold enum names so later epics (quiz types, bows,
 * spaced review) can add values without a breaking migration.
 */

@Entity(tableName = "lesson", indices = [Index("mdHash")])
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Subject label, e.g. "Istorie". */
    val subject: String,
    val title: String,
    val grade: Int?,
    val chapter: String?,
    /** The original Markdown file in app storage. */
    val mdPath: String,
    val mdHash: String,
    /** "draft" until the parent marks it ready. */
    val status: String,
    /** False when generating hints failed at import (no key, offline, rate limit). */
    val enriched: Boolean,
    val createdAt: Long,
) {
    companion object {
        const val DRAFT = "draft"
        const val READY = "ready"
    }
}

@Entity(
    tableName = "key_idea",
    foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lessonId")],
)
data class KeyIdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    /** Stable id inside the lesson: i1, i2, ... */
    val ideaKey: String,
    val ord: Int,
    val text: String,
    /** Keyword groups as JSON: [["Lucy","Lusi"],["Etiopia"]]. */
    val keywords: String,
    val broadQuestion: String,
    val clue: String,
    val explanation: String,
    val checkQuestion: String,
    val checkAnswer: String,
)

@Entity(
    tableName = "session",
    foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lessonId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val phase: String,
    /** The whole session state (SessionCodec), saved after every step: resume after a kill. */
    val state: String,
    val callsUsed: Int,
    val tokensUsed: Int,
    val score: Int,
    val total: Int,
)

@Entity(
    tableName = "turn",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val ord: Int,
    /** "capy" or "child". */
    val role: String,
    val text: String,
    /** "voice", "voiceFallback", "keyboard" (child turns only). */
    val inputMode: String?,
    val at: Long,
)

@Entity(
    tableName = "idea_result",
    primaryKeys = ["sessionId", "ideaKey"],
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
data class IdeaResultEntity(
    val sessionId: Long,
    val ideaKey: String,
    val ideaText: String,
    val retellStatus: String?,
    val outcome: String?,
    val hintsUsed: Int,
    /** "CORRECT", "NOT_YET", "UNJUDGED" or null. */
    val checkResult: String?,
    val childDisputed: Boolean,
    val evidence: String,
    /** The parent's decision on a disputed answer: "accepted", "rejected" or null. */
    val parentDecision: String?,
)

@Entity(tableName = "provider_usage", indices = [Index("date")])
data class ProviderUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** yyyy-MM-dd, local time. */
    val date: String,
    val provider: String,
    val model: String,
    val callType: String,
    val requests: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val audioSeconds: Int,
    val rateLimitedCount: Int,
    val outcome: String,
)

// ---- E11 · Quiz (database version 2) ---------------------------------------------------------

/** A lesson's quiz (QuizSchema JSON), prepared in advance or made by Groq. */
@Entity(
    tableName = "quiz",
    foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.CASCADE)],
)
data class QuizEntity(
    @PrimaryKey val lessonId: Long,
    val json: String,
    /** "prepared" (a .quiz.json file) or "groq". */
    val source: String,
    val createdAt: Long,
)

/** One go at a quiz; [state] is the whole QuizState (QuizStateCodec), saved after every answer. */
@Entity(
    tableName = "quiz_attempt",
    foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lessonId")],
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val state: String,
    val correct: Int,
    val total: Int,
)

// ---- E12 · Bows ----------------------------------------------------------------------------------

/**
 * Bows earned by one finished session. Kept even if the lesson is deleted later (no foreign key,
 * the title is copied): what the child earned is never taken away.
 */
@Entity(tableName = "bow_award", indices = [Index(value = ["sessionId"], unique = true), Index("lessonId")])
data class BowAwardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val lessonTitle: String,
    val sessionId: Long,
    /** 1–3 for this session. */
    val bows: Int,
    /** How many of them were new for the lesson (above its best so far); the total is the sum of these. */
    val added: Int,
    val at: Long,
)
