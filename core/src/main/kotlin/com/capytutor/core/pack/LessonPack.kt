package com.capytutor.core.pack

import com.capytutor.core.lesson.Lesson
import com.capytutor.core.schema.EnrichReply
import com.capytutor.core.schema.Schemas
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One key idea with everything generated at import (HANDOFF.md §9, KeyIdea). */
data class PackIdea(
    val id: String,
    val order: Int,
    val text: String,
    val keywords: List<List<String>>,
    val broadQuestion: String,
    val clue: String,
    val explanation: String,
    val checkQuestion: String,
    val checkAnswer: String,
)

/** A lesson ready for sessions: the parent's ideas plus the enrichment, tied to the file hash. */
data class LessonPack(val title: String, val lessonSha256: String, val ideas: List<PackIdea>) {

    fun toJson(): String = buildJsonObject {
        put("title", title)
        put("lessonSha256", lessonSha256)
        put(
            "ideas",
            buildJsonArray {
                for (i in ideas) add(
                    buildJsonObject {
                        put("id", i.id); put("order", i.order); put("text", i.text)
                        put("keywords", JsonArray(i.keywords.map { g -> JsonArray(g.map(::JsonPrimitive)) }))
                        put("broadQuestion", i.broadQuestion); put("clue", i.clue); put("explanation", i.explanation)
                        put("checkQuestion", i.checkQuestion); put("checkAnswer", i.checkAnswer)
                    },
                )
            },
        )
    }.toString()

    companion object {
        fun build(lesson: Lesson, enrich: EnrichReply): LessonPack {
            val byId = enrich.ideas.associateBy { it.id }
            val ideas = lesson.ideas.mapIndexed { index, text ->
                val id = lesson.ideaId(index)
                val e = byId[id] ?: error("Enrichment is missing $id")
                PackIdea(id, index, text, e.keywords, e.broadQuestion, e.clue, e.explanation, e.checkQuestion, e.checkAnswer)
            }
            return LessonPack(lesson.title, lesson.sha256, ideas)
        }

        fun fromJson(text: String): LessonPack {
            val o = Schemas.json.parseToJsonElement(text).jsonObject
            fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
            return LessonPack(
                title = o.s("title"),
                lessonSha256 = o.s("lessonSha256"),
                ideas = o.getValue("ideas").jsonArray.map { el ->
                    val i = el.jsonObject
                    PackIdea(
                        id = i.s("id"),
                        order = i.s("order").toInt(),
                        text = i.s("text"),
                        keywords = i.getValue("keywords").jsonArray.map { g -> g.jsonArray.map { it.jsonPrimitive.content } },
                        broadQuestion = i.s("broadQuestion"), clue = i.s("clue"), explanation = i.s("explanation"),
                        checkQuestion = i.s("checkQuestion"), checkAnswer = i.s("checkAnswer"),
                    )
                },
            )
        }
    }
}
