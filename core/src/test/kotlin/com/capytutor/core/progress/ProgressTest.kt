package com.capytutor.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    private fun e(at: Long, m: Mark) = Evidence(at, m)

    @Test
    fun sessionMarksFollowRecallAndTest() {
        assertEquals(Mark.ALONE, Progress.sessionMark("RECALLED_ALONE", "CORRECT", null))
        assertEquals(Mark.ALONE, Progress.sessionMark("RECALLED_ALONE", "UNJUDGED", null))
        assertEquals(Mark.WITH_HINT, Progress.sessionMark("RECALLED_WITH_HINT", "CORRECT", null))
        assertEquals(Mark.NOT_YET, Progress.sessionMark("RECALLED_ALONE", "NOT_YET", null))
        assertEquals(Mark.ALONE, Progress.sessionMark("RECALLED_ALONE", "NOT_YET", "accepted")) // the parent accepted her answer
        assertEquals(Mark.NOT_YET, Progress.sessionMark("EXPLAINED", "CORRECT", null))
        assertNull(Progress.sessionMark(null, null, null))
    }

    @Test
    fun statusComesFromTheLatestMarks() {
        assertEquals(IdeaStatus.UNSEEN, Progress.status(emptyList()))
        assertEquals(IdeaStatus.KNOWN, Progress.status(listOf(e(1, Mark.ALONE))))
        assertEquals(IdeaStatus.WEAK, Progress.status(listOf(e(1, Mark.ALONE), e(2, Mark.NOT_YET))))
        // Learned after a slip: known again.
        assertEquals(IdeaStatus.KNOWN, Progress.status(listOf(e(1, Mark.NOT_YET), e(2, Mark.QUIZ_RIGHT))))
        // Right once after two slips: still shaky.
        assertEquals(IdeaStatus.WEAK, Progress.status(listOf(e(1, Mark.NOT_YET), e(2, Mark.QUIZ_NOT_YET), e(3, Mark.WITH_HINT))))
        // Order is by time, not by list position.
        assertEquals(IdeaStatus.WEAK, Progress.status(listOf(e(5, Mark.QUIZ_NOT_YET), e(1, Mark.ALONE))))
    }
}
