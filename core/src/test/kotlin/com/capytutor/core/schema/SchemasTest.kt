package com.capytutor.core.schema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replies recorded in the shape Groq returns them (HANDOFF.md §8.2). No network. */
class SchemasTest {

    private val ids = listOf("i1", "i2")

    private val recall = """
        {"ideas":[{"id":"i2","status":"missed","evidence":"","note":""},
                  {"id":"i1","status":"covered","evidence":"primii oameni erau in africa","note":""}],
         "praise":"Ai ținut minte foarte bine unde au apărut primii oameni!"}
    """.trimIndent()

    @Test
    fun recallReplyIsParsedAndSortedInLessonOrder() {
        val r = Schemas.parseRecall(recall, ids)
        assertTrue(r.toString(), r is Validated.Ok)
        val reply = (r as Validated.Ok).value
        assertEquals(listOf("i1", "i2"), reply.ideas.map { it.id })
        assertEquals(CoverageStatus.COVERED, reply.ideas[0].status)
        assertEquals(CoverageStatus.MISSED, reply.ideas[1].status)
    }

    @Test
    fun fencedJsonIsAccepted() {
        assertTrue(Schemas.parseRecall("```json\n$recall\n```", ids) is Validated.Ok)
    }

    @Test
    fun missingIdeaIsInvalid() {
        val r = Schemas.parseRecall("""{"ideas":[{"id":"i1","status":"covered"}],"praise":"Bravo!"}""", ids)
        assertEquals(Validated.Invalid("missing ideas: i2"), r)
    }

    @Test
    fun unknownStatusIsInvalid() {
        val r = Schemas.parseRecall(
            """{"ideas":[{"id":"i1","status":"maybe"},{"id":"i2","status":"missed"}],"praise":"Bravo!"}""", ids,
        )
        assertTrue(r is Validated.Invalid && r.reason.contains("unknown status"))
    }

    @Test
    fun notJsonIsInvalid() {
        assertTrue(Schemas.parseRecall("Sigur! Iată răspunsul.", ids) is Validated.Invalid)
    }

    @Test
    fun enrichAcceptsGroupedAndFlatKeywords() {
        val raw = """
            {"ideas":[
              {"id":"i1","keywords":[["Africa","african"],["strămoși","primii oameni"]],"broadQuestion":"Unde?","clue":"Un continent.","explanation":"În Africa.","checkQuestion":"Unde au trăit?","checkAnswer":"În Africa"},
              {"id":"i2","keywords":["Lucy","Etiopia"],"broadQuestion":"Cine?","clue":"Un nume.","explanation":"Lucy.","checkQuestion":"Cum o cheamă?","checkAnswer":"Lucy"}
            ]}
        """.trimIndent()
        val r = Schemas.parseEnrich(raw, ids)
        assertTrue(r.toString(), r is Validated.Ok)
        val ideas = (r as Validated.Ok).value.ideas
        assertEquals(listOf(listOf("Africa", "african"), listOf("strămoși", "primii oameni")), ideas[0].keywords)
        assertEquals(listOf(listOf("Lucy"), listOf("Etiopia")), ideas[1].keywords)
    }

    @Test
    fun enrichWithEmptyFieldIsInvalid() {
        val raw = """{"ideas":[
            {"id":"i1","keywords":[["a"]],"broadQuestion":"","clue":"c","explanation":"e","checkQuestion":"q","checkAnswer":"a"},
            {"id":"i2","keywords":[["b"]],"broadQuestion":"b","clue":"c","explanation":"e","checkQuestion":"q","checkAnswer":"a"}]}"""
        val r = Schemas.parseEnrich(raw, ids)
        assertTrue(r is Validated.Invalid && r.reason.contains("broadQuestion"))
    }

    @Test
    fun answerAndSummaryReplies() {
        assertTrue(Schemas.parseAnswer("""{"status":"partial","reply":"Aproape! Mai gândește-te."}""") is Validated.Ok)
        assertTrue(Schemas.parseSummary("""{"childSummary":"Bravo!","parentNote":""}""") is Validated.Invalid)
    }
}
