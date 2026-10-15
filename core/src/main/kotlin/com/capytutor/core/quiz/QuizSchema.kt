package com.capytutor.core.quiz

import com.capytutor.core.RomanianText
import com.capytutor.core.schema.Schemas
import com.capytutor.core.schema.Validated
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The quiz file format, the same for a Groq reply and for `<lesson>.quiz.json` written in advance:
 *
 * ```json
 * {"questions": [
 *   {"type": "choice", "ideas": ["i1"], "question": "…?", "options": ["…", "…", "…"], "answer": "…", "explanation": "…"},
 *   {"type": "truefalse", "ideas": ["i2"], "statement": "…", "answer": true, "explanation": "…"},
 *   {"type": "order", "ideas": ["i3", "i5"], "question": "…", "items": ["first", "second", "third"], "explanation": "…"},
 *   {"type": "match", "ideas": ["i4"], "question": "…", "pairs": [{"left": "…", "right": "…"}], "explanation": "…"}
 * ]}
 * ```
 * A choice's `answer` is the text of the right option (models get indices wrong); order items
 * are listed in the right order. Everything is checked here; a quiz that fails is not used.
 */
object QuizSchema {
    val QUESTIONS = 5..10
    val OPTIONS = 3..4
    val ORDER_ITEMS = 3..5
    val MATCH_PAIRS = 3..4
    const val MAX_PROMPT = 220
    const val MAX_OPTION = 90
    const val MAX_EXPLANATION = 300

    private class QuizError(message: String) : Exception(message)

    private fun fail(msg: String): Nothing = throw QuizError(msg)

    fun parse(raw: String, lessonSha256: String, ideaIds: List<String>): Validated<Quiz> {
        val body = Schemas.extractJsonObject(raw) ?: return Validated.Invalid("no JSON object")
        val root = try {
            Schemas.json.parseToJsonElement(body) as? JsonObject ?: return Validated.Invalid("not a JSON object")
        } catch (e: Exception) {
            return Validated.Invalid("invalid JSON: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}")
        }
        return try {
            val list = root["questions"] as? JsonArray ?: fail("\"questions\" must be a list")
            val questions = list.mapIndexed { i, el -> question(el as? JsonObject ?: fail("questions[$i] must be an object"), "q${i + 1}", ideaIds.toSet()) }
            if (questions.size !in QUESTIONS) fail("a quiz has ${QUESTIONS.first}-${QUESTIONS.last} questions, got ${questions.size}")
            if (questions.map { it.type }.toSet().size < 2) fail("use at least two question types")
            val prompts = questions.map { QuizGrader.fold(it.prompt) }
            if (prompts.toSet().size != prompts.size) fail("two questions are the same")
            Validated.Ok(Quiz(lessonSha256, questions))
        } catch (e: QuizError) {
            Validated.Invalid(e.message ?: "quiz error")
        }
    }

    private fun question(o: JsonObject, where: String, known: Set<String>): QuizQuestion {
        val type = QuizType.fromWire(o.text("type", where)) ?: fail("$where: unknown type \"${o["type"]}\"")
        val ideas = (o["ideas"] as? JsonArray ?: fail("$where: \"ideas\" must be a list of idea ids"))
            .map { (it as? JsonPrimitive)?.content?.trim() ?: fail("$where: idea ids must be text") }
        if (ideas.isEmpty()) fail("$where: \"ideas\" is empty")
        ideas.firstOrNull { it !in known }?.let { fail("$where: unknown idea \"$it\"") }
        val explanation = o.text("explanation", where, MAX_EXPLANATION)
        return when (type) {
            QuizType.CHOICE -> {
                val prompt = o.text("question", where, MAX_PROMPT)
                val options = o.texts("options", where, MAX_OPTION)
                if (options.size !in OPTIONS) fail("$where: a choice has ${OPTIONS.first}-${OPTIONS.last} options")
                distinct(options, "$where: options")
                val answer = o.text("answer", where)
                val index = options.indexOfFirst { QuizGrader.fold(it) == QuizGrader.fold(answer) }
                if (index < 0) fail("$where: the answer is not one of the options")
                QuizQuestion.Choice(ideas, prompt, options, index, explanation)
            }
            QuizType.TRUE_FALSE -> {
                val statement = o.text("statement", where, MAX_PROMPT)
                val answer = (o["answer"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
                    ?: fail("$where: \"answer\" must be true or false")
                QuizQuestion.TrueFalse(ideas, statement, answer, explanation)
            }
            QuizType.ORDER -> {
                val prompt = o.text("question", where, MAX_PROMPT)
                val items = o.texts("items", where, MAX_OPTION)
                if (items.size !in ORDER_ITEMS) fail("$where: an order question has ${ORDER_ITEMS.first}-${ORDER_ITEMS.last} items")
                distinct(items, "$where: items")
                QuizQuestion.Order(ideas, prompt, items, explanation)
            }
            QuizType.MATCH -> {
                val prompt = o.text("question", where, MAX_PROMPT)
                val pairs = (o["pairs"] as? JsonArray ?: fail("$where: \"pairs\" must be a list")).mapIndexed { p, el ->
                    val po = el as? JsonObject ?: fail("$where: pairs[$p] must be an object")
                    po.text("left", where, MAX_OPTION) to po.text("right", where, MAX_OPTION)
                }
                if (pairs.size !in MATCH_PAIRS) fail("$where: a match question has ${MATCH_PAIRS.first}-${MATCH_PAIRS.last} pairs")
                distinct(pairs.map { it.first }, "$where: left side")
                distinct(pairs.map { it.second }, "$where: right side")
                QuizQuestion.Match(ideas, prompt, pairs, explanation)
            }
        }
    }

    private fun distinct(values: List<String>, where: String) {
        val folded = values.map(QuizGrader::fold)
        if (folded.toSet().size != folded.size) fail("$where repeat")
    }

    private fun JsonObject.text(key: String, where: String, max: Int = Int.MAX_VALUE): String {
        val el = this[key]
        val s = when {
            el == null || el is JsonNull -> null
            el is JsonPrimitive -> RomanianText.normalizeDiacritics(el.content).trim()
            else -> fail("$where: \"$key\" must be text")
        }
        if (s.isNullOrEmpty()) fail("$where: \"$key\" is missing or empty")
        if (s.length > max) fail("$where: \"$key\" is too long (${s.length} > $max)")
        return s
    }

    private fun JsonObject.texts(key: String, where: String, max: Int): List<String> =
        (this[key] as? JsonArray ?: fail("$where: \"$key\" must be a list")).map { el: JsonElement ->
            val s = (el as? JsonPrimitive)?.content?.let(RomanianText::normalizeDiacritics)?.trim()
            if (s.isNullOrEmpty()) fail("$where: \"$key\" has an empty entry")
            if (s.length > max) fail("$where: \"$key\" entry is too long (${s.length} > $max)")
            s
        }

    /** The same format back (stored in Room, read with [parse]). */
    fun encode(quiz: Quiz): String = buildJsonObject {
        put(
            "questions",
            buildJsonArray {
                for (q in quiz.questions) add(
                    buildJsonObject {
                        put("type", q.type.wire)
                        put("ideas", JsonArray(q.ideaIds.map(::JsonPrimitive)))
                        when (q) {
                            is QuizQuestion.Choice -> {
                                put("question", q.prompt)
                                put("options", JsonArray(q.options.map(::JsonPrimitive)))
                                put("answer", q.options[q.answer])
                            }
                            is QuizQuestion.TrueFalse -> {
                                put("statement", q.prompt)
                                put("answer", q.answer)
                            }
                            is QuizQuestion.Order -> {
                                put("question", q.prompt)
                                put("items", JsonArray(q.items.map(::JsonPrimitive)))
                            }
                            is QuizQuestion.Match -> {
                                put("question", q.prompt)
                                put("pairs", JsonArray(q.pairs.map { (l, r) -> buildJsonObject { put("left", l); put("right", r) } }))
                            }
                        }
                        put("explanation", q.explanation)
                    },
                )
            },
        )
    }.toString()
}
