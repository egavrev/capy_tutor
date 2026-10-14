package com.capytutor.core.session

import com.capytutor.core.schema.CoverageStatus
import com.capytutor.core.schema.Schemas
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Saves a [SessionState] as JSON and reads it back, so a session can be stored (Room, M3)
 * and resumed in the same state after the app is killed, backgrounded or paused.
 */
object SessionCodec {
    const val VERSION = 1

    fun encode(s: SessionState): String = buildJsonObject {
        put("v", VERSION)
        put("lessonSha256", s.lessonSha256)
        put("phase", s.phase.name)
        put("results", JsonArray(s.results.map(::result)))
        put("retelling", s.retelling)
        put("praise", s.praise)
        put("gaps", JsonArray(s.gaps.map(::JsonPrimitive)))
        put("gapIndex", s.gapIndex)
        put("hintStep", s.hintStep.name)
        put("testIndex", s.testIndex)
        put("feedback", s.feedback?.let { f -> buildJsonObject { put("ideaId", f.ideaId); put("result", f.result.name); put("text", f.text) } } ?: JsonNull)
        put("disputable", s.disputable)
        put("pending", s.pending?.let(::request) ?: JsonNull)
        put("paused", s.paused?.name)
        put("mood", s.mood.name)
        put("callsUsed", s.callsUsed)
        put("tokensUsed", s.tokensUsed)
        put("childSummary", s.childSummary)
        put("parentNote", s.parentNote)
        put(
            "turns",
            JsonArray(
                s.turns.map { t ->
                    buildJsonObject { put("speaker", t.speaker.name); put("text", t.text); put("mode", t.inputMode?.name) }
                },
            ),
        )
    }.toString()

    fun decode(text: String): SessionState {
        val o = Schemas.json.parseToJsonElement(text).jsonObject
        require(o.int("v") == VERSION) { "Unknown session format ${o["v"]}" }
        return SessionState(
            lessonSha256 = o.str("lessonSha256")!!,
            phase = Phase.valueOf(o.str("phase")!!),
            results = o.getValue("results").jsonArray.map { it.jsonObject.toResult() },
            retelling = o.str("retelling").orEmpty(),
            praise = o.str("praise").orEmpty(),
            gaps = o.getValue("gaps").jsonArray.map { it.jsonPrimitive.content },
            gapIndex = o.int("gapIndex"),
            hintStep = HintStep.valueOf(o.str("hintStep")!!),
            testIndex = o.int("testIndex"),
            feedback = (o["feedback"] as? JsonObject)?.let { Feedback(it.str("ideaId")!!, CheckResult.valueOf(it.str("result")!!), it.str("text")!!) },
            disputable = o.str("disputable"),
            pending = (o["pending"] as? JsonObject)?.toRequest(),
            paused = o.str("paused")?.let(PauseReason::valueOf),
            mood = CapyMood.valueOf(o.str("mood")!!),
            callsUsed = o.int("callsUsed"),
            tokensUsed = o.int("tokensUsed"),
            childSummary = o.str("childSummary").orEmpty(),
            parentNote = o.str("parentNote").orEmpty(),
            turns = o.getValue("turns").jsonArray.map { el ->
                val t = el.jsonObject
                Turn(Speaker.valueOf(t.str("speaker")!!), t.str("text")!!, t.str("mode")?.let(InputMode::valueOf))
            },
        )
    }

    private fun result(r: IdeaResult) = buildJsonObject {
        put("ideaId", r.ideaId)
        put("retellStatus", r.retellStatus?.name)
        put("evidence", r.evidence)
        put("outcome", r.outcome?.name)
        put("hintsUsed", r.hintsUsed)
        put("checkResult", r.checkResult?.name)
        put("childDisputed", r.childDisputed)
    }

    private fun JsonObject.toResult() = IdeaResult(
        ideaId = str("ideaId")!!,
        retellStatus = str("retellStatus")?.let(CoverageStatus::valueOf),
        evidence = str("evidence").orEmpty(),
        outcome = str("outcome")?.let(Outcome::valueOf),
        hintsUsed = int("hintsUsed"),
        checkResult = str("checkResult")?.let(CheckResult::valueOf),
        childDisputed = getValue("childDisputed").jsonPrimitive.boolean,
    )

    private fun request(r: Request): JsonElement = when (r) {
        is Request.AssessRecall -> buildJsonObject { put("type", "assessRecall"); put("transcript", r.transcript) }
        is Request.JudgeAnswer -> buildJsonObject {
            put("type", "judgeAnswer"); put("ideaId", r.ideaId); put("question", r.question)
            put("answer", r.answer); put("purpose", r.purpose.name)
        }
        Request.Summarize -> buildJsonObject { put("type", "summarize") }
    }

    private fun JsonObject.toRequest(): Request = when (val type = str("type")) {
        "assessRecall" -> Request.AssessRecall(str("transcript")!!)
        "judgeAnswer" -> Request.JudgeAnswer(str("ideaId")!!, str("question")!!, str("answer")!!, Purpose.valueOf(str("purpose")!!))
        "summarize" -> Request.Summarize
        else -> error("Unknown request type $type")
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
}
