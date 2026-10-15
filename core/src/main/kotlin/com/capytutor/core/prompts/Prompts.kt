package com.capytutor.core.prompts

import com.capytutor.core.config.CoreConfig

/**
 * Prompt templates live as versioned files in `src/main/resources/prompts/<name>.<version>.txt`.
 * Placeholders are written `{{name}}`; rendering fails if any is left unfilled.
 */
object Prompts {
    const val SYSTEM = "system"
    const val ENRICH_IDEAS = "enrich_ideas"
    const val ASSESS_RECALL = "assess_recall"
    const val ASSESS_ANSWER = "assess_answer"
    const val SUMMARIZE_SESSION = "summarize_session"

    // Every brace escaped: Android's regex engine (ICU) rejects a bare "}", the desktop JVM does not.
    private val placeholder = Regex("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}")

    fun version(name: String): String = CoreConfig.get("prompt.$name") ?: "v1"

    fun template(name: String, version: String = version(name)): String {
        val path = "/prompts/$name.$version.txt"
        val stream = Prompts::class.java.getResourceAsStream(path)
            ?: error("Prompt file not found: $path")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }.trimEnd()
    }

    fun system(): String = template(SYSTEM)

    fun render(name: String, values: Map<String, String>): String = fill(template(name), values)

    fun fill(template: String, values: Map<String, String>): String {
        val missing = placeholder.findAll(template).map { it.groupValues[1] }.filter { it !in values }.toSet()
        require(missing.isEmpty()) { "Prompt placeholders without a value: ${missing.joinToString()}" }
        return placeholder.replace(template) { values.getValue(it.groupValues[1]) }
    }
}
