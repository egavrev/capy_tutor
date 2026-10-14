package com.capytutor.core.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Model replies and their schemas (HANDOFF.md §8.2). Every reply is decoded from the JSON
 * tree by hand and validated here; anything that fails goes back to the model once with a
 * reminder. Hand decoding (no codegen) keeps the error messages specific.
 */

sealed interface Validated<out T> {
    data class Ok<T>(val value: T) : Validated<T>
    data class Invalid(val reason: String) : Validated<Nothing>
}

inline fun <T, R> Validated<T>.map(f: (T) -> R): Validated<R> = when (this) {
    is Validated.Ok -> Validated.Ok(f(value))
    is Validated.Invalid -> this
}

enum class CoverageStatus {
    COVERED, PARTIAL, MISSED, WRONG;

    val wire: String get() = name.lowercase()
    val isKnown: Boolean get() = this == COVERED

    companion object {
        fun fromWire(s: String): CoverageStatus? = entries.firstOrNull { it.wire == s.trim().lowercase() }
    }
}

data class EnrichReply(val ideas: List<EnrichedIdea>)

data class EnrichedIdea(
    val id: String,
    /** Keyword groups: each group lists equivalent forms of one concept. */
    val keywords: List<List<String>>,
    val broadQuestion: String,
    val clue: String,
    val explanation: String,
    val checkQuestion: String,
    val checkAnswer: String,
)

data class RecallReply(val ideas: List<RecallIdea>, val praise: String)

data class RecallIdea(val id: String, val status: CoverageStatus, val evidence: String, val note: String)

data class AnswerReply(val status: CoverageStatus, val reply: String)

data class SummaryReply(val childSummary: String, val parentNote: String)

/** Thrown inside decoding; turned into [Validated.Invalid]. */
private class SchemaError(message: String) : Exception(message)

object Schemas {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }

    const val MIN_KEYWORD_GROUPS = 1
    const val MAX_KEYWORD_GROUPS = 6

    fun parseEnrich(raw: String, expectedIds: List<String>): Validated<EnrichReply> = decode(raw) { root ->
        val ideas = root.array("ideas").mapIndexed { i, el ->
            val o = el.obj("ideas[$i]")
            val id = o.string("id")
            val groups = o.array("keywords").mapIndexed { g, kw ->
                when (kw) {
                    is JsonPrimitive -> listOf(kw.content) // a flat list: each string is its own group
                    is JsonArray -> kw.map { (it as? JsonPrimitive)?.content ?: fail("$id: keyword must be a string") }
                    else -> fail("$id: keywords[$g] must be a list of strings")
                }.map { it.trim() }.filter { it.isNotEmpty() }
            }.filter { it.isNotEmpty() }
            if (groups.size !in MIN_KEYWORD_GROUPS..MAX_KEYWORD_GROUPS) {
                fail("$id: keywords must have $MIN_KEYWORD_GROUPS-$MAX_KEYWORD_GROUPS groups, got ${groups.size}")
            }
            EnrichedIdea(
                id = id,
                keywords = groups,
                broadQuestion = o.string("broadQuestion"),
                clue = o.string("clue"),
                explanation = o.string("explanation"),
                checkQuestion = o.string("checkQuestion"),
                checkAnswer = o.string("checkAnswer"),
            )
        }
        checkIds(ideas.map { it.id }, expectedIds)
        EnrichReply(ideas.sortedBy { expectedIds.indexOf(it.id) })
    }

    fun parseRecall(raw: String, expectedIds: List<String>): Validated<RecallReply> = decode(raw) { root ->
        val ideas = root.array("ideas").mapIndexed { i, el ->
            val o = el.obj("ideas[$i]")
            val id = o.string("id")
            RecallIdea(
                id = id,
                status = o.status("status", id),
                evidence = o.string("evidence", required = false),
                note = o.string("note", required = false),
            )
        }
        checkIds(ideas.map { it.id }, expectedIds)
        RecallReply(ideas.sortedBy { expectedIds.indexOf(it.id) }, root.string("praise"))
    }

    fun parseAnswer(raw: String): Validated<AnswerReply> = decode(raw) { root ->
        AnswerReply(root.status("status", "answer"), root.string("reply"))
    }

    fun parseSummary(raw: String): Validated<SummaryReply> = decode(raw) { root ->
        SummaryReply(root.string("childSummary"), root.string("parentNote"))
    }

    /** Strips ```json fences or stray text around the outermost JSON object. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
    }

    // ---- encoding helpers used when building prompts and caches ----

    fun ideasForPrompt(ideas: List<Triple<String, String, List<List<String>>>>): String =
        buildJsonArray {
            for ((id, text, keywords) in ideas) {
                add(
                    buildJsonObject {
                        put("id", id)
                        put("text", text)
                        put("keywords", JsonArray(keywords.flatten().distinct().map(::JsonPrimitive)))
                    },
                )
            }
        }.toString()

    // ---- decoding plumbing ----

    private fun <T> decode(raw: String, block: (JsonObject) -> T): Validated<T> {
        val body = extractJsonObject(raw) ?: return Validated.Invalid("no JSON object in reply")
        val root = try {
            json.parseToJsonElement(body) as? JsonObject ?: return Validated.Invalid("reply is not a JSON object")
        } catch (e: Exception) {
            return Validated.Invalid("invalid JSON: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}")
        }
        return try {
            Validated.Ok(block(root))
        } catch (e: SchemaError) {
            Validated.Invalid(e.message ?: "schema error")
        }
    }

    private fun fail(msg: String): Nothing = throw SchemaError(msg)

    private fun checkIds(got: List<String>, expected: List<String>) {
        val missing = expected - got.toSet()
        val extra = got.toSet() - expected.toSet()
        val dupes = got.groupBy { it }.filterValues { it.size > 1 }.keys
        when {
            missing.isNotEmpty() -> fail("missing ideas: ${missing.joinToString()}")
            extra.isNotEmpty() -> fail("unknown ideas: ${extra.joinToString()}")
            dupes.isNotEmpty() -> fail("duplicate ideas: ${dupes.joinToString()}")
        }
    }

    private fun JsonElement.obj(where: String): JsonObject = this as? JsonObject ?: fail("$where must be an object")

    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: fail("\"$key\" must be a list")

    private fun JsonObject.string(key: String, required: Boolean = true): String {
        val el = this[key]
        val s = when {
            el == null || el is JsonNull -> null
            el is JsonPrimitive -> el.content
            else -> fail("\"$key\" must be text")
        }?.trim()
        if (required && s.isNullOrEmpty()) fail("\"$key\" is missing or empty")
        return s.orEmpty()
    }

    private fun JsonObject.status(key: String, owner: String): CoverageStatus {
        val s = string(key)
        return CoverageStatus.fromWire(s) ?: fail("$owner: unknown status \"$s\"")
    }
}
