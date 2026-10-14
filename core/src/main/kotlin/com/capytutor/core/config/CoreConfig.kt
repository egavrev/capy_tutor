package com.capytutor.core.config

import java.util.Properties

/** A call type and the provider/model that serves it, e.g. groq + openai/gpt-oss-120b. */
data class Route(val callType: String, val provider: String, val model: String) {
    val id: String get() = "$provider/$model"
}

/**
 * Reads `config/providers.properties` from the classpath (HANDOFF.md §4.2).
 * Model ids live there, never in logic.
 */
object CoreConfig {
    const val ENRICH_IDEAS = "enrich_ideas"
    const val ASSESS_RECALL = "assess_recall"
    const val ASSESS_ANSWER = "assess_answer"
    const val SUMMARIZE_SESSION = "summarize_session"
    const val TRANSCRIBE = "transcribe"

    private val props: Properties by lazy {
        Properties().apply {
            CoreConfig::class.java.getResourceAsStream("/config/providers.properties")
                ?.reader(Charsets.UTF_8)?.use { load(it) }
                ?: error("config/providers.properties not found on the classpath")
        }
    }

    fun get(key: String): String? = props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

    fun route(callType: String): Route {
        val value = get(callType) ?: error("No provider configured for $callType")
        val slash = value.indexOf('/')
        require(slash > 0 && slash < value.length - 1) { "Bad route for $callType: $value" }
        return Route(callType, value.substring(0, slash), value.substring(slash + 1))
    }

    fun maxTokens(callType: String, default: Int = 800): Int = get("$callType.maxTokens")?.toIntOrNull() ?: default

    fun temperature(): Double = get("temperature")?.toDoubleOrNull() ?: 0.2
}
