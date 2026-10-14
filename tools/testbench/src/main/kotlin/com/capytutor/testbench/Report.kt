package com.capytutor.testbench

import com.capytutor.core.config.CoreConfig
import com.capytutor.core.coverage.KeywordMatcher
import com.capytutor.core.lesson.Lesson
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.prompts.Prompts
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Markdown reports for the owner to review. */
object Report {

    fun header(title: String): String = buildString {
        val chat = CoreConfig.route(CoreConfig.ASSESS_RECALL)
        val enrich = CoreConfig.route(CoreConfig.ENRICH_IDEAS)
        appendLine("# $title")
        appendLine()
        appendLine(
            "Model `${chat.id}` · reasoning: enrich `${CoreConfig.reasoningEffort(enrich.callType, enrich.provider) ?: "default"}`, " +
                "assess `${CoreConfig.reasoningEffort(chat.callType, chat.provider) ?: "default"}` · " +
                "prompts `system ${Prompts.version(Prompts.SYSTEM)}`, `enrich_ideas ${Prompts.version(Prompts.ENRICH_IDEAS)}`, " +
                "`assess_recall ${Prompts.version(Prompts.ASSESS_RECALL)}` · " +
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")),
        )
        appendLine()
    }

    fun enrich(lesson: Lesson, pack: LessonPack?, outcome: EnrichOutcome): String = buildString {
        append(header("Enrich: ${lesson.title}"))
        appendLine("${lesson.subject.label} · ${lesson.ideas.size} ideas · ${lesson.wordCount} words · `${outcome.record.outcome}` · " +
            "${outcome.record.latencyMs} ms · ${outcome.record.inputTokens} in / ${outcome.record.outputTokens} out tokens" +
            (if (outcome.record.rateLimitWaits > 0) " · ${outcome.record.rateLimitWaits} rate-limit waits" else "") +
            (outcome.record.retryReason?.let { " · first reply rejected: $it" } ?: ""))
        appendLine()
        if (pack == null) {
            appendLine("**Enrichment failed:** ${outcome.error}")
            return@buildString
        }
        val leaks = pack.ideas.associate { i ->
            i.id to listOfNotNull(
                KeywordMatcher.revealedWord(i.broadQuestion, i.checkAnswer)?.let { "question: „$it”" },
                KeywordMatcher.revealedWord(i.clue, i.checkAnswer)?.let { "clue: „$it”" },
            )
        }
        val leaking = leaks.count { it.value.isNotEmpty() }
        appendLine("**Hints that reveal the answer: $leaking of ${pack.ideas.size}** (the question or clue contains a word of the check answer).")
        appendLine()
        appendLine("| Id | Idea (parent) | Keyword groups | Broad question | Clue | Reveals answer? | Explanation | Check question | Check answer |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (i in pack.ideas) {
            val kw = i.keywords.joinToString("<br>") { g -> g.joinToString(" / ") }
            val leak = leaks.getValue(i.id).joinToString("<br>").ifEmpty { "–" }
            appendLine("| ${i.id} | ${cell(i.text)} | ${cell(kw)} | ${cell(i.broadQuestion)} | ${cell(i.clue)} | ${cell(leak)} | ${cell(i.explanation)} | ${cell(i.checkQuestion)} | ${cell(i.checkAnswer)} |")
        }
        appendLine()
        appendLine("Keyword groups: each line is one concept; any of its forms counts. All groups present = keyword candidate *covered*. " +
            "A revealed word is flagged even when it only names the topic, so read the flagged rows.")
    }

    /** Known (covered) vs not known for each idea the parent marked. */
    fun agrees(o: AssessOutcome): Boolean? {
        val reply = o.reply ?: return null
        if (o.retelling.expected.isEmpty()) return null
        return reply.ideas.all { i -> o.retelling.expected[i.id]?.let { it.isKnown == i.status.isKnown } ?: true }
    }

    fun assess(pack: LessonPack, outcomes: List<AssessOutcome>): String = buildString {
        append(header("Assess: ${pack.title}"))

        val judged = outcomes.mapNotNull { agrees(it) }
        appendLine("## Agreement with the parent's marks")
        appendLine()
        appendLine("A retelling agrees when, for every idea, Capy and the parent both say *known* (covered) or both say *to practise* (partial, missed or wrong).")
        appendLine()
        appendLine("| Retelling | Agrees | Known: parent | Known: Capy | Exact status matches | Differences |")
        appendLine("|---|---|---|---|---|---|")
        for (o in outcomes) {
            val r = o.reply
            val exp = o.retelling.expected
            if (r == null) {
                appendLine("| ${o.retelling.id} ${cell(o.retelling.label)} | error | | | | ${cell(o.error ?: "")} |")
                continue
            }
            val diffs = r.ideas.filter { i -> exp[i.id]?.let { it.isKnown != i.status.isKnown } == true }
                .joinToString(", ") { "${it.id}: Capy ${it.status.wire}, parent ${exp.getValue(it.id).wire}" }
            val exact = r.ideas.count { exp[it.id] == it.status }
            appendLine(
                "| ${o.retelling.id} ${cell(o.retelling.label)} | ${when (agrees(o)) { true -> "yes"; false -> "**no**"; null -> "–" }} | " +
                    "${exp.values.count { it.isKnown }}/${exp.size} | ${r.ideas.count { it.status.isKnown }}/${r.ideas.size} | " +
                    "$exact/${exp.size} | ${cell(diffs.ifEmpty { "–" })} |",
            )
        }
        appendLine()
        appendLine("**${judged.count { it }} of ${judged.size} retellings agree.**")
        appendLine()

        appendLine("## Idea × retelling")
        appendLine()
        appendLine("Cell = Capy's status · `kw a/b` = keyword groups found (all found = candidate covered) · `≠ x` = the parent marked x.")
        appendLine()
        appendLine("| Idea | " + outcomes.joinToString(" | ") { it.retelling.id } + " |")
        appendLine("|---|" + outcomes.joinToString("") { "---|" })
        for (idea in pack.ideas) {
            val cells = outcomes.map { o ->
                val status = o.reply?.ideas?.firstOrNull { it.id == idea.id }?.status
                val (m, t) = o.keywordMatched[idea.id] ?: (0 to 0)
                val exp = o.retelling.expected[idea.id]
                val base = status?.wire ?: "–"
                val mark = if (exp != null && status != null && exp.isKnown != status.isKnown) "**$base** ≠ ${exp.wire}"
                else if (exp != null && status != null && exp != status) "$base (≈ ${exp.wire})"
                else base
                "$mark · kw $m/$t"
            }
            appendLine("| ${idea.id} ${cell(idea.text.take(48) + if (idea.text.length > 48) "…" else "")} | ${cells.joinToString(" | ")} |")
        }
        appendLine()

        appendLine("## Calls")
        appendLine()
        appendLine("| Retelling | Words | Latency | Tokens in / out | Retries | Rate-limit waits | Outcome | Capy's praise |")
        appendLine("|---|---|---|---|---|---|---|---|")
        for (o in outcomes) {
            val rec = o.record
            appendLine(
                "| ${o.retelling.id} | ${o.sentText.split(' ').size} | ${rec.latencyMs} ms | ${rec.inputTokens} / ${rec.outputTokens} | " +
                    "${rec.retries} | ${rec.rateLimitWaits} | ${cell(rec.outcome)} | ${cell(o.reply?.praise ?: "")} |",
            )
        }
        appendLine()

        appendLine("## Details")
        for (o in outcomes) {
            appendLine()
            appendLine("### ${o.retelling.id} · ${o.retelling.label}")
            appendLine()
            appendLine("> ${o.sentText}")
            appendLine()
            val r = o.reply
            if (r == null) {
                appendLine("Error: ${o.error}")
                continue
            }
            appendLine("| Idea | Capy | Parent | Evidence | Note |")
            appendLine("|---|---|---|---|---|")
            for (i in r.ideas) {
                appendLine("| ${i.id} | ${i.status.wire} | ${o.retelling.expected[i.id]?.wire ?: "–"} | ${cell(i.evidence)} | ${cell(i.note)} |")
            }
        }
    }

    fun summary(sections: List<Pair<String, List<AssessOutcome>>>, records: List<CallRecord>): String = buildString {
        append(header("Test bench summary"))
        val all = sections.flatMap { it.second }
        val judged = all.mapNotNull { agrees(it) }
        appendLine("**Gap marking agrees with the parent on ${judged.count { it }} of ${judged.size} retellings** (target: at least 8 of 10).")
        appendLine()
        appendLine("| Lesson | Agrees |")
        appendLine("|---|---|")
        for ((title, outs) in sections) {
            val j = outs.mapNotNull { agrees(it) }
            appendLine("| ${cell(title)} | ${j.count { it }} of ${j.size} |")
        }
        appendLine()
        appendLine("## Cost and speed")
        appendLine()
        appendLine("| Call | Count | Avg latency | Max latency | Avg tokens in / out | Retries | Rate-limit waits | Failures |")
        appendLine("|---|---|---|---|---|---|---|---|")
        for ((type, rs) in records.groupBy { it.callType }) {
            appendLine(
                "| $type | ${rs.size} | ${rs.map { it.latencyMs }.average().toLong()} ms | ${rs.maxOf { it.latencyMs }} ms | " +
                    "${rs.map { it.inputTokens }.average().toInt()} / ${rs.map { it.outputTokens }.average().toInt()} | " +
                    "${rs.sumOf { it.retries }} | ${rs.sumOf { it.rateLimitWaits }} | ${rs.count { !it.outcome.startsWith("ok") }} |",
            )
        }
        appendLine()
        appendLine("Totals: ${records.size} calls, ${records.sumOf { it.inputTokens }} input + ${records.sumOf { it.outputTokens }} output tokens.")
        appendLine()
        appendLine("Session budget (HANDOFF §8): at most 25 LLM calls and 40,000 tokens per session; one `assess_recall` is one of those calls.")
    }

    private fun cell(s: String): String = s.replace("|", "\\|").replace("\n", " ").trim()
}
