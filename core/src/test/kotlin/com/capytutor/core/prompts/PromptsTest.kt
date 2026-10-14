package com.capytutor.core.prompts

import com.capytutor.core.config.CoreConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test
    fun systemPromptIsTheSharedCapyPrompt() {
        val s = Prompts.system()
        assertTrue(s.startsWith("Ești Capy"))
        assertTrue(s.contains("Încă nu. Hai să ne uităm împreună."))
    }

    @Test
    fun templatesRenderWithoutLeftoverPlaceholders() {
        val enrich = Prompts.render(Prompts.ENRICH_IDEAS, mapOf("title" to "T", "text" to "X", "ideas" to "i1: A"))
        val recall = Prompts.render(Prompts.ASSESS_RECALL, mapOf("title" to "T", "ideas" to "[]", "transcript" to "Y"))
        for (p in listOf(enrich, recall)) assertFalse(p.contains("{{"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingValueFails() {
        Prompts.render(Prompts.ASSESS_RECALL, mapOf("title" to "T"))
    }

    @Test
    fun routesComeFromConfiguration() {
        val r = CoreConfig.route(CoreConfig.ASSESS_RECALL)
        assertEquals("groq", r.provider)
        assertEquals("openai/gpt-oss-120b", r.model)
        assertEquals("whisper-large-v3-turbo", CoreConfig.route(CoreConfig.TRANSCRIBE).model)
    }

    @Test
    fun overridesWinOverTheFileAndCanBeCleared() {
        try {
            CoreConfig.set("prompt.assess_recall", "v1")
            assertEquals("v1", Prompts.version(Prompts.ASSESS_RECALL))
            assertTrue(Prompts.render(Prompts.ASSESS_RECALL, mapOf("title" to "T", "ideas" to "[]", "transcript" to "Y")).isNotBlank())
        } finally {
            CoreConfig.clearOverrides()
        }
        assertEquals("v2", Prompts.version(Prompts.ASSESS_RECALL))
        assertEquals("medium", CoreConfig.reasoningEffort(CoreConfig.ENRICH_IDEAS, "groq"))
        assertEquals("low", CoreConfig.reasoningEffort(CoreConfig.ASSESS_RECALL, "groq"))
    }
}
