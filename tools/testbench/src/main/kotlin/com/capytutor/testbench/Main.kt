package com.capytutor.testbench

import com.capytutor.ai.SttResult
import com.capytutor.ai.groq.Groq
import com.capytutor.ai.groq.GroqWhisperStt
import com.capytutor.ai.http.OkHttpTransport
import com.capytutor.core.config.CoreConfig
import com.capytutor.core.lesson.Lesson
import com.capytutor.core.lesson.LessonParser
import com.capytutor.core.lesson.ParseResult
import com.capytutor.core.pack.LessonPack
import com.capytutor.core.safety.SafetyFilter
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """
Capy Tutor test bench

  all                                   enrich every lessons/**/SAMPLE-*.md and assess it with
                                        tools/testbench/samples/<lesson>.retellings.md
  enrich <lesson.md>                    generate keywords, hints and questions for a lesson
  assess <lesson.md> <retellings.md>    judge each retelling against the lesson's key ideas
  transcribe <audio> [--lesson <md>]    Groq Whisper, Romanian

Options:
  --out <dir>          where reports go (default tools/testbench/out)
  --fresh              ignore cached enrichment (out/<lesson>.pack.json)
  --child-name <name>  replaced with "copilul" before sending (repeatable)
  --set <key>=<value>  override a setting from config/providers.properties, e.g.
                       --set prompt.assess_recall=v1   --set assess_recall.reasoningEffort=medium

The Groq key comes from GROQ_API_KEY or secrets.properties. It is never printed.
"""

private class Options(args: List<String>) {
    val positional = mutableListOf<String>()
    var out = File("tools/testbench/out")
    var fresh = false
    var lessonForPrompt: File? = null
    val childNames = mutableListOf<String>()

    init {
        val it = args.iterator()
        while (it.hasNext()) {
            when (val a = it.next()) {
                "--out" -> out = File(it.next())
                "--fresh" -> fresh = true
                "--lesson" -> lessonForPrompt = File(it.next())
                "--child-name" -> childNames += it.next()
                "--set" -> it.next().split('=', limit = 2).let { (k, v) -> CoreConfig.set(k.trim(), v.trim()) }
                "-h", "--help" -> { println(USAGE); exitProcess(0) }
                else -> positional += a
            }
        }
    }
}

fun main(args: Array<String>) {
    // Romanian output regardless of the terminal's default encoding.
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
    System.setErr(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"))
    val opts = Options(args.toList())
    val command = opts.positional.firstOrNull() ?: run { println(USAGE); exitProcess(2) }
    val key = Secrets.groqKey() ?: run {
        System.err.println("GROQ_API_KEY is not set. Export it, or copy secrets.properties.example to secrets.properties and fill it in.")
        exitProcess(2)
    }
    opts.out.mkdirs()

    val transport = OkHttpTransport()
    val llmFor = { callType: String ->
        val route = CoreConfig.route(callType)
        require(route.provider == "groq") { "Only Groq is supported in the MVP (got ${route.provider} for $callType)" }
        Groq.chat(
            model = route.model,
            apiKey = { key },
            transport = transport,
            baseUrl = CoreConfig.get("groq.baseUrl") ?: Groq.DEFAULT_BASE_URL,
            timeoutMs = 60_000, // generous: the bench measures latency; the app uses 20 s
            reasoningEffort = CoreConfig.reasoningEffort(callType, route.provider),
        )
    }
    val bench = Bench(llmFor, SafetyFilter(opts.childNames))

    val ok = runBlocking {
        when (command) {
            "enrich" -> enrichCommand(bench, opts, file(opts, 1)) != null
            "assess" -> assessCommand(bench, opts, file(opts, 1), file(opts, 2)) != null
            "all" -> allCommand(bench, opts)
            "transcribe" -> transcribeCommand(key, transport, opts, file(opts, 1))
            else -> { println(USAGE); false }
        }
    }
    exitProcess(if (ok) 0 else 1)
}

private fun file(opts: Options, index: Int): File {
    val path = opts.positional.getOrNull(index) ?: run { println(USAGE); exitProcess(2) }
    return File(path).also { if (!it.isFile) { System.err.println("Not found: $path"); exitProcess(2) } }
}

private fun loadLesson(f: File): Lesson? = when (val r = LessonParser.parse(f.readText())) {
    is ParseResult.Ok -> r.lesson
    is ParseResult.Error -> { System.err.println("${f.name}: ${r.message}"); null }
}

private suspend fun enrichCommand(bench: Bench, opts: Options, lessonFile: File): LessonPack? {
    val lesson = loadLesson(lessonFile) ?: return null
    val cache = File(opts.out, "${lessonFile.nameWithoutExtension}.pack.json")
    if (!opts.fresh && cache.isFile) {
        val cached = runCatching { LessonPack.fromJson(cache.readText()) }.getOrNull()
        if (cached != null && cached.lessonSha256 == lesson.sha256) {
            println("${lessonFile.name}: using cached enrichment (${cache.name}); --fresh to regenerate")
            return cached
        }
    }
    println("${lessonFile.name}: enriching ${lesson.ideas.size} ideas")
    val outcome = bench.enrich(lesson)
    File(opts.out, "${lessonFile.nameWithoutExtension}.enrich.md").writeText(Report.enrich(lesson, outcome.pack, outcome))
    outcome.pack?.let { cache.writeText(it.toJson()) }
    if (outcome.pack == null) System.err.println("${lessonFile.name}: enrichment failed: ${outcome.error}")
    return outcome.pack
}

private suspend fun assessCommand(bench: Bench, opts: Options, lessonFile: File, retellingsFile: File): List<AssessOutcome>? {
    val pack = enrichCommand(bench, opts, lessonFile) ?: return null
    val retellings = Retellings.parse(retellingsFile.readText())
    println("${lessonFile.name}: assessing ${retellings.size} retellings")
    val outcomes = retellings.map { bench.assess(pack, it) }
    File(opts.out, "${lessonFile.nameWithoutExtension}.assess.md").writeText(Report.assess(pack, outcomes))
    return outcomes
}

private suspend fun allCommand(bench: Bench, opts: Options): Boolean {
    val lessons = File("lessons").walk().filter { it.isFile && it.name.startsWith("SAMPLE-") && it.extension == "md" }.sortedBy { it.path }.toList()
    if (lessons.isEmpty()) { System.err.println("No lessons/**/SAMPLE-*.md found (run from the repository root)"); return false }
    val sections = mutableListOf<Pair<String, List<AssessOutcome>>>()
    var ok = true
    for (lessonFile in lessons) {
        val retellings = File("tools/testbench/samples/${lessonFile.nameWithoutExtension}.retellings.md")
        val outcomes = if (retellings.isFile) {
            assessCommand(bench, opts, lessonFile, retellings)
        } else {
            println("${lessonFile.name}: no retellings file, enrichment only"); enrichCommand(bench, opts, lessonFile)?.let { emptyList() }
        }
        if (outcomes == null) { ok = false; continue }
        sections += (loadLesson(lessonFile)?.title ?: lessonFile.name) to outcomes
        if (outcomes.any { it.error != null }) ok = false
    }
    File(opts.out, "SUMMARY.md").writeText(Report.summary(sections, bench.records))
    println("Reports written to ${opts.out.path}")
    return ok
}

private suspend fun transcribeCommand(key: String, transport: OkHttpTransport, opts: Options, audio: File): Boolean {
    val route = CoreConfig.route(CoreConfig.TRANSCRIBE)
    val stt = GroqWhisperStt(route.model, { key }, transport, CoreConfig.get("groq.baseUrl") ?: Groq.DEFAULT_BASE_URL)
    val hint = opts.lessonForPrompt?.let { loadLesson(it) }?.let { l -> (listOf(l.title) + l.terms.map { it.term }).joinToString(", ") }
    val start = System.nanoTime()
    val r = stt.transcribe(audio, "ro", hint)
    val ms = (System.nanoTime() - start) / 1_000_000
    val line = when (r) {
        is SttResult.Ok -> "| ${audio.name} | ok | $ms ms | ${r.text.replace("|", "\\|")} |"
        is SttResult.RateLimited -> "| ${audio.name} | rate limited (${r.retryAfterSeconds ?: "?"} s) | $ms ms | |"
        is SttResult.Failed -> "| ${audio.name} | failed: ${r.reason} | $ms ms | |"
    }
    val report = Report.header("Transcribe") + "| File | Outcome | Latency | Text |\n|---|---|---|---|\n$line\n"
    File(opts.out, "${audio.nameWithoutExtension}.transcribe.md").writeText(report)
    println(line)
    return r is SttResult.Ok
}
