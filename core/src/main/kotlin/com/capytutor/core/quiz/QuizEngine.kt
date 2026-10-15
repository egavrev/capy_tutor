package com.capytutor.core.quiz

import com.capytutor.core.schema.Schemas
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * One attempt at a quiz: which question is shown, what was answered, how it went. Saved after
 * every step, so a quiz survives the app being killed. [index] == question count means done.
 */
data class QuizState(
    val lessonSha256: String,
    /** Shuffles options, items and the right-hand column; the same seed shows the same order after a restart. */
    val seed: Long,
    val index: Int,
    val results: List<Boolean?>,
    /** The current question was answered and its feedback is showing. */
    val checked: Boolean = false,
    val lastAnswer: QuizAnswer? = null,
) {
    val total: Int get() = results.size
    val correct: Int get() = results.count { it == true }
    val done: Boolean get() = index >= results.size
}

object QuizEngine {
    fun start(quiz: Quiz, seed: Long = Random.nextLong()): QuizState =
        QuizState(quiz.lessonSha256, seed, 0, List(quiz.questions.size) { null })

    fun answer(quiz: Quiz, s: QuizState, a: QuizAnswer): QuizState {
        if (s.done || s.checked) return s
        val right = QuizGrader.isRight(quiz.questions[s.index], a)
        return s.copy(results = s.results.toMutableList().also { it[s.index] = right }, checked = true, lastAnswer = a)
    }

    /** "Mai departe" after the feedback. */
    fun next(s: QuizState): QuizState =
        if (s.done || !s.checked) s else s.copy(index = s.index + 1, checked = false, lastAnswer = null)

    /**
     * The order the choices are shown in, as original indices: options for a choice, items for
     * an order question, the right-hand column for a match. Never already in the right order
     * for order and match questions (that would give the answer away).
     */
    fun shown(q: QuizQuestion, seed: Long, index: Int): List<Int> {
        val n = when (q) {
            is QuizQuestion.Choice -> q.options.size
            is QuizQuestion.TrueFalse -> return listOf(0, 1)
            is QuizQuestion.Order -> q.items.size
            is QuizQuestion.Match -> q.pairs.size
        }
        val rnd = Random(seed * 31 + index)
        val identity = (0 until n).toList()
        var p = identity.shuffled(rnd)
        if (q !is QuizQuestion.Choice && p == identity) p = identity.drop(1) + identity.first()
        return p
    }
}

/** [QuizState] as JSON, for Room. */
object QuizStateCodec {
    const val VERSION = 1

    fun encode(s: QuizState): String = buildJsonObject {
        put("v", VERSION)
        put("lessonSha256", s.lessonSha256)
        put("seed", s.seed)
        put("index", s.index)
        put("results", JsonArray(s.results.map { if (it == null) JsonNull else JsonPrimitive(it) }))
        put("checked", s.checked)
        put(
            "lastAnswer",
            when (val a = s.lastAnswer) {
                null -> JsonNull
                is QuizAnswer.Choice -> buildJsonObject { put("t", "choice"); put("v", a.option) }
                is QuizAnswer.TrueFalse -> buildJsonObject { put("t", "truefalse"); put("v", a.value) }
                is QuizAnswer.Order -> buildJsonObject { put("t", "order"); put("v", JsonArray(a.items.map(::JsonPrimitive))) }
                is QuizAnswer.Match -> buildJsonObject { put("t", "match"); put("v", JsonArray(a.rights.map(::JsonPrimitive))) }
            },
        )
    }.toString()

    fun decode(text: String): QuizState {
        val o = Schemas.json.parseToJsonElement(text).jsonObject
        require(o.getValue("v").jsonPrimitive.int == VERSION) { "Unknown quiz state format" }
        val last = o["lastAnswer"] as? JsonObject
        fun ints() = last!!.getValue("v").jsonArray.map { it.jsonPrimitive.int }
        return QuizState(
            lessonSha256 = o.getValue("lessonSha256").jsonPrimitive.content,
            seed = o.getValue("seed").jsonPrimitive.long,
            index = o.getValue("index").jsonPrimitive.int,
            results = o.getValue("results").jsonArray.map { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.boolean },
            checked = o.getValue("checked").jsonPrimitive.boolean,
            lastAnswer = when (last?.get("t")?.jsonPrimitive?.content) {
                "choice" -> QuizAnswer.Choice(last.getValue("v").jsonPrimitive.int)
                "truefalse" -> QuizAnswer.TrueFalse(last.getValue("v").jsonPrimitive.boolean)
                "order" -> QuizAnswer.Order(ints())
                "match" -> QuizAnswer.Match(ints())
                else -> null
            },
        )
    }
}
