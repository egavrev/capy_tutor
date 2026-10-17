package com.capytutor.data.repo

import com.capytutor.core.pack.LessonPack
import com.capytutor.core.reward.Bows
import com.capytutor.data.db.BowAwardEntity
import com.capytutor.core.session.Phase
import com.capytutor.core.session.SessionCodec
import com.capytutor.core.session.SessionState
import com.capytutor.core.session.Speaker
import com.capytutor.data.db.CapyDatabase
import com.capytutor.data.db.IdeaResultEntity
import com.capytutor.data.db.SessionEntity
import com.capytutor.data.db.TurnEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sessions are saved after every step (the whole state as JSON plus the turns and per-idea
 * results as rows), so the app can be killed at any moment and resume in the same place.
 */
class SessionRepository(private val db: CapyDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val sessions = db.sessions()

    fun observeOpenLessonIds(): Flow<List<Long>> = sessions.observeOpenLessonIds()
    fun observeSessions(lessonId: Long): Flow<List<SessionEntity>> = sessions.observeFor(lessonId)
    fun observeResults(sessionId: Long): Flow<List<IdeaResultEntity>> = sessions.observeResults(sessionId)
    suspend fun results(sessionId: Long): List<IdeaResultEntity> = sessions.results(sessionId)

    /** The unfinished session for this lesson, if any. */
    suspend fun open(lessonId: Long): Pair<Long, SessionState>? {
        val s = sessions.openFor(lessonId) ?: return null
        val state = runCatching { SessionCodec.decode(s.state) }.getOrNull() ?: return null
        return s.id to state
    }

    /** Drops an unfinished attempt ("start from the lesson again"); a finished one is never touched. */
    suspend fun discard(sessionId: Long) = sessions.deleteOpen(sessionId)

    suspend fun create(lessonId: Long, state: SessionState): Long = sessions.insert(
        SessionEntity(
            lessonId = lessonId, startedAt = clock(), endedAt = null, phase = state.phase.name,
            state = SessionCodec.encode(state), callsUsed = 0, tokensUsed = 0, score = 0, total = state.results.size,
        ),
    )

    suspend fun save(sessionId: Long, pack: LessonPack, state: SessionState) {
        val row = sessions.get(sessionId) ?: return
        val done = state.phase == Phase.DONE || state.phase == Phase.SUMMARY
        sessions.update(
            row.copy(
                phase = state.phase.name,
                state = SessionCodec.encode(state),
                callsUsed = state.callsUsed,
                tokensUsed = state.tokensUsed,
                score = state.score,
                total = state.results.size,
                endedAt = if (state.phase == Phase.DONE) row.endedAt ?: clock() else null,
            ),
        )
        val saved = sessions.turnCount(sessionId)
        if (state.turns.size > saved) {
            val now = clock()
            sessions.insertTurns(
                state.turns.drop(saved).mapIndexed { i, t ->
                    TurnEntity(
                        sessionId = sessionId, ord = saved + i,
                        role = if (t.speaker == Speaker.CAPY) "capy" else "child",
                        text = t.text,
                        inputMode = t.inputMode?.name?.lowercase(),
                        at = now,
                    )
                },
            )
        }
        if (done) award(sessionId, row.lessonId, pack.title, state)
        val texts = pack.ideas.associate { it.id to it.text }
        if (done || state.results.any { it.retellStatus != null }) {
            val previous = sessions.results(sessionId).associateBy { it.ideaKey }
            sessions.upsertResults(
                state.results.map { r ->
                    IdeaResultEntity(
                        sessionId = sessionId, ideaKey = r.ideaId, ideaText = texts[r.ideaId].orEmpty(),
                        retellStatus = r.retellStatus?.wire, outcome = r.outcome?.name, hintsUsed = r.hintsUsed,
                        checkResult = r.checkResult?.name, childDisputed = r.childDisputed, evidence = r.evidence,
                        parentDecision = previous[r.ideaId]?.parentDecision,
                    )
                },
            )
        }
    }

    /** E12: a finished session earns 1–3 bows, once; only bows above the lesson's best count toward the total. */
    private suspend fun award(sessionId: Long, lessonId: Long, title: String, state: SessionState) {
        val bows = db.bows()
        if (bows.forSession(sessionId) != null) return
        val earned = Bows.forSession(state.score, state.results.size)
        val best = bows.best(lessonId) ?: 0
        bows.insert(
            BowAwardEntity(
                lessonId = lessonId, lessonTitle = title, sessionId = sessionId,
                bows = earned, added = Bows.added(best, earned), at = clock(),
            ),
        )
    }

    /** The bows a session earned, once it is finished. */
    suspend fun bowsFor(sessionId: Long): BowAwardEntity? = db.bows().forSession(sessionId)

    /** The parent accepts or rejects an answer the child disputed ("Cred că am dreptate"). */
    suspend fun decide(sessionId: Long, ideaKey: String, accept: Boolean) {
        sessions.decide(sessionId, ideaKey, if (accept) "accepted" else "rejected")
    }
}
