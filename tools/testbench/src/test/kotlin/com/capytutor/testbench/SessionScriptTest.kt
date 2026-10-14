package com.capytutor.testbench

import com.capytutor.core.session.HintStep
import com.capytutor.core.session.IdeaResult
import com.capytutor.core.session.Input
import com.capytutor.core.session.Phase
import com.capytutor.core.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SessionScriptTest {

    private val base = SessionState("sha", Phase.RETELLING, listOf(IdeaResult("i1"), IdeaResult("i2")))

    @Test
    fun typedLinesBecomeInputs() {
        assertEquals(Input.Continue, TypedInput.parse(""))
        assertEquals(Input.FinishRetelling, TypedInput.parse("/gata"))
        assertEquals(Input.DontKnow, TypedInput.parse("Nu știu"))
        assertEquals(Input.DontKnow, TypedInput.parse("nu-mi amintesc."))
        assertEquals(Input.Dispute, TypedInput.parse("Cred că am dreptate"))
        assertEquals(null, TypedInput.parse("/stop"))
        assertEquals(Input.Say("în Africa"), TypedInput.parse(" în Africa "))
    }

    @Test
    fun scriptAnswersBySituationAndUsesEachLineOnce() {
        val script = SessionScript(
            """
            # comment
            retell: prima bucată
            retell: a doua bucată
            i2.broad: un răspuns   # note
            i1.test: Africa
            i1.feedback: /dreptate
            """.trimIndent(),
        )
        assertEquals(Input.Say("prima bucată", com.capytutor.core.session.InputMode.VOICE), script.next(base))
        script.next(base)
        assertEquals(Input.FinishRetelling, script.next(base))
        val hints = base.copy(phase = Phase.HINTS, gaps = listOf("i2"), hintStep = HintStep.BROAD)
        assertEquals(Input.Say("un răspuns"), script.next(hints))
        assertEquals(Input.DontKnow, script.next(hints.copy(hintStep = HintStep.CLUE)))
        val test = base.copy(phase = Phase.TEST, testIndex = 0)
        assertEquals(Input.Say("Africa"), script.next(test))
        val fb = test.copy(feedback = com.capytutor.core.session.Feedback("i1", com.capytutor.core.session.CheckResult.NOT_YET, "x"))
        assertEquals(Input.Dispute, script.next(fb))
        assertEquals(Input.Continue, script.next(fb))
    }

    @Test
    fun sampleSessionScriptsExistForBothLessons() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "lessons").isDirectory }
        val scripts = File(root, "tools/testbench/samples").listFiles { f -> f.name.endsWith(".session.md") }.orEmpty()
        assertEquals(2, scripts.size)
    }
}
