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
    const val MAKE_QUIZ = "make_quiz"
    const val TRANSCRIBE = "transcribe"

    private val props: Properties by lazy {
        Properties().apply {
            CoreConfig::class.java.getResourceAsStream("/config/providers.properties")
                ?.reader(Charsets.UTF_8)?.use { load(it) }
                ?: error("config/providers.properties not found on the classpath")
        }
    }

    private val overrides = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun get(key: String): String? =
        (overrides[key] ?: props.getProperty(key))?.trim()?.takeIf { it.isNotEmpty() }

    /** Overrides one setting for this process (test bench `--set key=value`). Never used by the app. */
    fun set(key: String, value: String) {
        overrides[key] = value
    }

    fun clearOverrides() = overrides.clear()

    /** The providers for [callType], in the order to try them. */
    fun routes(callType: String): List<Route> {
        val value = get(callType) ?: error("No provider configured for $callType")
        return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val slash = entry.indexOf('/')
            require(slash > 0 && slash < entry.length - 1) { "Bad route for $callType: $entry" }
            Route(callType, entry.substring(0, slash), entry.substring(slash + 1))
        }
    }

    fun route(callType: String): Route = routes(callType).first()

    fun timeoutMs(callType: String): Long =
        (get("$callType.timeoutMs") ?: get("timeoutMs"))?.toLongOrNull() ?: 20_000L

    fun maxTokens(callType: String, default: Int = 800): Int = get("$callType.maxTokens")?.toIntOrNull() ?: default

    /** Per-call reasoning effort (e.g. enrich_ideas.reasoningEffort), else the provider default. */
    fun reasoningEffort(callType: String, provider: String): String? =
        get("$callType.reasoningEffort") ?: get("$provider.reasoningEffort")

    fun temperature(): Double = get("temperature")?.toDoubleOrNull() ?: 0.2
}
