# Capy Tutor: MVP build handoff (v2)

The full specification of the first MVP. Read it together with `CLAUDE.md` (standing rules) and `design-system/DESIGN_SYSTEM.md` (how every screen looks). Decisions behind it: `docs/architecture-decisions.md`.

---

## 1. What we are building

**Capy Tutor** (UI name: *Capy Tutore*) is an Android app that helps a 5th grader (age 10–12, Moldova, Romanian language) check that she really remembers a textbook lesson.

The child retells a lesson out loud. Capy, a calm capybara mascot, checks which key ideas she said, helps her recall the missed ones with hints (never by giving the answer first), and ends with one short question per key idea.

It is a family app: one child, one phone, installed as an APK on a Family Link phone. No Play Store, no accounts, no backend.

## 2. Hard constraints

- **Kotlin + Jetpack Compose + Material 3**, single activity, Navigation Compose. minSdk 26, targetSdk the latest stable. Gradle Kotlin DSL, version catalog, Gradle wrapper committed.
- **Application id and package: `com.capytutor`.** The design-system theme files ship with the placeholder package `md.capy.tutore`; change the `package` line and the `R` import.
- **No Android Studio.** Everything builds from the command line. **GitHub Actions** (repo `egavrev/capy_tutor`) runs the unit tests and builds debug and release APKs on every push to `main`, and pushes the APKs to a `builds` branch (same pattern as the owner's Capy Pomodoro app).
- **Release signing:** keystore in `signing/` (git-ignored) locally; CI reads it from secrets `CAPY_TUTOR_KEYSTORE_B64` and `CAPY_TUTOR_KEYSTORE_PASSWORD`. Without secrets, CI builds debug only.
- **Free services only:** Groq's free plan for the LLM and speech-to-text; Android's built-in voice for text-to-speech. No other paid or free cloud service, no analytics, no crash reporting.
- **Romanian UI** with correct diacritics (ș ț with comma below, never ş ţ). Code, comments and logs in English.
- **Design system is law:** follow `design-system/DESIGN_SYSTEM.md`. No hard-coded colours, sizes or fonts.
- **Privacy:** the child's name never leaves the phone; audio is sent only to Groq for transcription and deleted right after; the API key is never logged, committed or shown in full.
- **Calm by design:** no timers, no streaks, no notifications, no red, no shaking. 200–300 ms ease-out motion only, none when "Remove animations" is on.

## 3. The MVP flow

1. **Parent imports a lesson** (Markdown, section 6) in parent mode. The file must contain the parent's own `## Idei cheie` list. The app runs one Groq call (`enrich_ideas`) to add, per idea: keywords, a broad question, a clue, a short explanation and a check question. The parent reviews and can edit everything, then marks the lesson ready.
2. **Child opens a ready lesson.** Capy: "Povestește-mi cu cuvintele tale tot ce ții minte."
3. **Retelling.** The child holds the mic and talks (or types). On release the audio goes to Groq Whisper; the transcript appears in her bubble and she can correct it.
4. **Check.** Keyword pass on the phone, then one `assess_recall` call. Each idea becomes `covered`, `partial`, `missed` or `wrong`. Capy praises one specific thing first, then shows the idea chips: known ones with `checkheart`, the rest with `sprout`.
5. **Guided questions (hint ladder)**, one gap at a time, in lesson order: broad question → clue → Capy explains in 1–2 sentences and asks her to say it back. Each answer is judged against that one idea (keyword pass first, then `assess_answer` only if needed). Recalled at any step → next gap.
6. **Final test: one question per key idea**, in lesson order, using each idea's `checkQuestion`. Each answer gets immediate feedback in the feedback bottom sheet ("Corect!" or "Încă nu. Răspunsul este: …"). Keyword pass first; `assess_answer` only when keywords are inconclusive.
7. **Summary:** score "N din M", ideas recalled alone / with a hint / explained by Capy, and a short note for the parent. Capy happy (≥ 70%) or encouraging (below). Bows and Capy's Corner are **not** in the MVP: no bow counter, no bow row.

There is **no session length target**: never show time estimates or pressure.

## 4. Architecture

Gradle modules:

| Module | Contents | Android? |
|---|---|---|
| `:core` | Lesson parser, lesson pack builder, session engine, coverage judge, safety filter, prompt templates, JSON schemas | No: plain Kotlin/JVM |
| `:ai` | `LlmProvider`, `SpeechToText`, `TextToSpeech` interfaces; `ProviderRouter`; `OpenAiCompatibleLlm` (Groq chat); `GroqWhisperStt` | No for interfaces and HTTP |
| `:data` | Room database, Markdown file store, encrypted settings and key storage | Yes |
| `:app` | Compose screens, view models, navigation, `AndroidSpeechStt` fallback, `AndroidTts`, audio recorder, DI wiring | Yes |

Plus `tools/testbench/`: a JVM command-line app reusing `:core` and `:ai` (milestone M1).

Libraries: coroutines + Flow, kotlinx.serialization, OkHttp (or Ktor client), Room, DataStore, Navigation Compose. DI by hand or Hilt; keep it small. **Ask before adding any other dependency.**

### 4.1 Interfaces

```kotlin
interface LlmProvider {
    val id: String                                   // "groq/openai/gpt-oss-120b"
    suspend fun completeJson(system: String, user: String, schemaName: String,
                             maxTokens: Int = 800, temperature: Double = 0.2): LlmResult
}

sealed interface LlmResult {
    data class Ok(val json: String, val inputTokens: Int, val outputTokens: Int) : LlmResult
    data class RateLimited(val retryAfterSeconds: Int?) : LlmResult
    data class Failed(val reason: String, val retryable: Boolean) : LlmResult
}

interface SpeechToText {
    val id: String
    suspend fun transcribe(audio: File, languageTag: String = "ro"): SttResult   // batch (Groq Whisper)
}

sealed interface SttResult {
    data class Ok(val text: String) : SttResult
    data class RateLimited(val retryAfterSeconds: Int?) : SttResult
    data class Failed(val reason: String) : SttResult
}

interface TextToSpeech {
    val id: String
    fun isAvailable(): Boolean
    suspend fun speak(text: String, languageTag: String = "ro-RO")
    fun stop()
}
```

`AndroidSpeechStt` (live `SpeechRecognizer`, `ro-RO`) also implements a streaming variant used only as the fallback (section 4.4).

### 4.2 Provider router

- Each call type has an ordered list of `(provider, model)` entries read from configuration. **MVP: one entry each, no fallback provider** (decision 1). The router stays, so a second provider can be added later without touching callers.
- On a JSON reply that fails schema validation: retry once on the same model with a short "reply only with valid JSON" reminder.
- On `RateLimited` or a timeout (20 s): **pause gracefully**. Capy says "Capy e puțin obosit. Continuăm mai târziu?", the session is saved for resume, and for the current step the keyword-only result is used where it exists.
- Every call is recorded in `ProviderUsage` (provider, model, date, requests, input and output tokens, outcome).

```
enrich_ideas      : groq/openai/gpt-oss-120b
assess_recall     : groq/openai/gpt-oss-120b
assess_answer     : groq/openai/gpt-oss-120b
summarize_session : groq/openai/gpt-oss-120b
transcribe        : groq/whisper-large-v3-turbo
```

Model ids live in configuration, never in logic. The test bench (M1) confirms them.

### 4.3 Groq

- **Chat:** OpenAI-compatible `POST https://api.groq.com/openai/v1/chat/completions`, header `Authorization: Bearer <key>`. Ask for JSON (`response_format: {"type": "json_object"}`) and always validate against the schema yourself.
- **Speech-to-text:** `POST https://api.groq.com/openai/v1/audio/transcriptions`, multipart: `file`, `model=whisper-large-v3-turbo`, `language=ro`, `response_format=json`, `temperature=0`. Optionally pass `prompt` with the lesson title and a few key terms, which improves recognition of names and dates.
- **Free plan limits (per model):** gpt-oss-120b: 30 requests/min, 1,000 requests/day, 8,000 tokens/min, 200,000 tokens/day. Whisper: 20 requests/min, 2,000 requests/day, 7,200 audio seconds/hour, 28,800 audio seconds/day. Read `retry-after` on HTTP 429. Limits can change: the app must survive a 429 at any moment.
- **Never** use the Gemini API in this app (its terms exclude apps used by under-18s).

### 4.4 Speech

- **Recording:** hold-to-talk on the mic button (tap-to-toggle also allowed). Record mono, 16 kHz, AAC in an `.m4a` file in the app's cache directory. Cap a single recording at 3 minutes; Capy gently asks to continue in a second recording if needed.
- **Transcription [decision 4]:** `GroqWhisperStt` by default. Show Capy `listening` while recording and `thinking` while transcribing. **Delete the audio file as soon as the transcript returns**, success or failure.
- **Fallback:** when there is no internet or Groq returns 429, switch to `AndroidSpeechStt` (live `SpeechRecognizer`, `ro-RO`, prefer on-device / offline) if Romanian recognition is available; otherwise show "Scrie răspunsul" and the keyboard.
- **Text-to-speech [decision 7]:** Android `TextToSpeech`, `Locale("ro", "RO")`, **off by default**; the speaker button on each Capy bubble reads it aloud. If no Romanian voice is installed, hide speaker buttons and show a hint in parent mode.
- **Permissions:** request `RECORD_AUDIO` only when the mic is first tapped, with a Romanian explanation. Mic without permission: inactive style + "Permite microfonul".
- **Phone check [decision 3]:** parent mode has a "Verificare telefon" screen showing Android version, microphone permission, Groq reachability, Romanian TTS voice present, Romanian offline speech recognition present.

## 5. Session engine

Pure Kotlin state machine in `:core`, unit-tested with recorded replies.

```
Intro -> Retelling -> Transcribing -> Assessing -> ShowingCoverage
  -> for each gap: HintBroad -> Answer -> HintClue -> Answer -> Explaining -> SayBack -> Answer
  -> for each idea: CheckQuestion -> Answer -> Feedback
  -> Summary -> Done
Any state -> Paused (app backgrounded, rate-limited, offline) -> resumes in the same state
```

Fixed in code, not left to the model:

- Gaps one at a time, in lesson order; at most 3 hint steps per idea; the explanation only at step 3.
- Outcome per idea: `recalledAlone` (covered in the retelling), `recalledWithHint` (at the broad question or clue), `explained` (needed step 3). Plus `checkCorrect` true/false from the final test.
- "Nu știu" / "Nu-mi amintesc" moves to the next step without judging.
- "Cred că am dreptate" on a not-yet answer: the parent can later accept it in parent mode; in the session it counts as accepted and is flagged.
- No timers, no "wrong" wording. A `wrong` answer gets a gentle correction.

## 6. Lesson Markdown format

The owner produces lessons with `docs/lesson-extraction-prompt.md`. Template: `lessons/TEMPLATE-lectie.md`.

```markdown
---
subject: Istorie
grade: 5
chapter: Preistoria
title: Apariția omului
language: ro
---

# Apariția omului

Lesson text in paragraphs...

## Idei cheie

- One key idea per line, one short sentence.

## Termeni

- **fosilă**: urmă a unei ființe păstrată în piatră
```

- Required: `title`, ≥ 100 words of text, and **`## Idei cheie` with 3–15 items [decision 5]**. Missing or empty list → reject the import with a plain Romanian message; do not extract ideas automatically.
- Ignore HTML comments (`<!-- -->`). Accept both ș/ț and the cedilla forms on input, but normalise to comma-below.
- `subject` is one of Istorie, Română, Matematică, Științe, Geografie. **First test subjects: Istorie and Geografie [decision 12].**
- Store the original file and its SHA-256. Re-importing a changed file rebuilds the pack; keep results linked by idea text where it matches.
- Import with the Android file picker (`ACTION_OPEN_DOCUMENT`, `text/markdown`, `text/plain`) and "Share to Capy Tutore".

## 7. Coverage judging

1. **Keyword pass** (free, on the phone): lower case, strip diacritics for matching only, drop filler ("păi", "deci", "ăă", "și apoi"). All of an idea's required keywords present → candidate `covered`.
2. **LLM pass** (`assess_recall` / `assess_answer`): judges meaning and the child's own words; the only one that may decide `missed`, `partial` or `wrong`.

Tell the model the text is a speech transcript of a 10-year-old and may contain recognition errors. Before any call, the **safety filter** replaces the child's name with "copilul" and removes anything that looks like a phone number, address or email.

## 8. Prompts

Prompt texts live in `:core/src/main/resources/prompts/` as versioned files; every reply is JSON validated against a schema in `:core`.

### 8.1 Shared system prompt

```
Ești Capy, un capibara calm și prietenos care ajută un copil de clasa a 5-a să verifice dacă a învățat o lecție.
Reguli:
- Vorbești doar în limba română, cu "tu", în propoziții scurte (cel mult 15 cuvinte).
- Lauzi efortul și strategia, niciodată inteligența ("Ai explicat foarte clar!", nu "Ești genial!").
- Nu spui niciodată "Greșit". Spui "Încă nu. Hai să ne uităm împreună."
- Nu dai răspunsul înainte de pasul de explicație.
- Vorbești doar despre lecția dată. Dacă copilul întreabă altceva, îl readuci blând la lecție.
- Răspunzi DOAR cu JSON valid, după schema cerută, fără alt text.
```

### 8.2 Calls

| Call | When | Input | Output (JSON) |
|---|---|---|---|
| `enrich_ideas` | Lesson import (once, cached) | title, lesson text, ideas | per idea: `keywords` (3–6, with synonyms and inflected forms), `broadQuestion`, `clue`, `explanation` (1–2 sentences), `checkQuestion` (short open question whose answer is the idea), `checkAnswer` (expected answer, few words) |
| `assess_recall` | After the retelling | ideas (id, text, keywords), transcript | `{"ideas":[{"id":"i1","status":"covered|partial|missed|wrong","evidence":"...","note":"..."}],"praise":"..."}` |
| `assess_answer` | Hint answer or test answer, only when the keyword pass is inconclusive | one idea, the question asked, the answer | `{"status":"covered|partial|missed|wrong","reply":"..."}` (`reply`: Capy's next line, max 2 sentences) |
| `summarize_session` | End | all results | `{"childSummary":"...","parentNote":"..."}` |

Example `assess_recall`:

```json
{"lessonTitle":"Apariția omului",
 "ideas":[{"id":"i1","text":"Primii oameni au apărut în Africa.","keywords":["Africa","primii oameni"]},
          {"id":"i2","text":"Lucy este o fosilă găsită în Etiopia.","keywords":["Lucy","fosilă","Etiopia"]}],
 "transcript":"primii oameni erau in africa si faceau unelte din piatra"}
```
```json
{"ideas":[{"id":"i1","status":"covered","evidence":"primii oameni erau in africa","note":""},
          {"id":"i2","status":"missed","evidence":"","note":""}],
 "praise":"Ai ținut minte foarte bine unde au apărut primii oameni!"}
```

**Budget per session:** at most 25 LLM calls and 40,000 tokens (typical: 1 `assess_recall` + a few `assess_answer` + 1 summary, because the keyword pass settles most test answers). Send ideas and the transcript, not the whole lesson, after import.

## 9. Data (Room)

| Entity | Fields |
|---|---|
| `Subject` | id, name, colourToken |
| `Lesson` | id, subjectId, title, grade, chapter, mdPath, mdHash, status (`draft`, `ready`), createdAt |
| `KeyIdea` | id, lessonId, order, text, keywords, broadQuestion, clue, explanation, checkQuestion, checkAnswer |
| `Session` | id, lessonId, startedAt, endedAt, state (for resume), callsUsed, tokensUsed |
| `Turn` | id, sessionId, role (`capy`, `child`), text, inputMode (`voice`, `voiceFallback`, `keyboard`), at |
| `IdeaResult` | sessionId, ideaId, retellStatus, outcome, hintsUsed, checkCorrect, childDisputed, evidence |
| `ProviderUsage` | provider, model, date, requests, inputTokens, outputTokens, audioSeconds, rateLimitedCount |

Design so later epics (quiz types, bows, spaced review) need no breaking migration.

**Settings and secrets [decision 8]:** child's name, parent PIN (hashed), Groq API key. Stored with an Android Keystore-backed key (for example Tink + DataStore). Never in `BuildConfig`, resources, logs or crash output. Show only the key's last 4 characters.

## 10. Design system

`design-system/DESIGN_SYSTEM.md` is the single source of truth. Read it before any UI work. The owner puts the full package (PDF guide, `tokens.json`, `res/` files: colours, themes, dimens, Nunito font, `ic_capy_*` icons, `bg_floral_stripes.jpg`, `capy_*.png`) in `design-system/`. Copy its resources into `app/src/main/res/` and its Compose theme into `app/src/main/java/com/capytutor/ui/theme/`.

If any part of the package is missing when you need it, stop and ask; do not invent colours or icons.

Use the component recipes from DESIGN_SYSTEM.md as reusable composables in `ui/components/`: primary/secondary/text button, mic button, Capy bubble, child bubble, idea chip, feedback bottom sheet, lesson row, banners, PIN pad, 4-step progress (Lecție · Povestește · Întrebări · Test). Skip for the MVP: bow counter, bow row, bottom navigation's Recapitulare and Colțul lui Capy tabs (show only Acasă; no bottom navigation needed).

## 11. Screens (MVP)

1. **Acasă:** "Bună, Vali!" (child's name from settings), lessons grouped by subject with the subject dot, each row ready / "de verificat"; empty state with Capy `reading`; small lock icon for parent mode.
2. **Lecția e gata:** title, the 4-step progress, number of ideas as empty chips, "Hai să începem".
3. **Povestește:** Capy bubble with the request, child bubbles, 96 dp mic (recording rings), keyboard toggle, editable transcript, "Gata, am terminat".
4. **Ce ai ținut minte:** idea chips (known / to practise), Capy's praise, "Hai să le căutăm împreună" (or straight to the test if nothing is missing).
5. **Întrebări (hint ladder):** chat layout, Capy bubbles with hints, mic and keyboard, "Nu-mi amintesc".
6. **Test:** one question per key idea, answer by voice or keyboard, feedback bottom sheet after each.
7. **Rezumat:** "N din M", outcomes list, Capy happy or encouraging, "Gata pe azi".
8. **Mod părinte** behind a 4-digit PIN (set on first launch): import lesson, review and edit key ideas and generated hints/questions, lesson history (last results per idea, disputed answers to accept or reject), child's name, Groq API key, Capy voice on/off, usage today (requests, tokens, audio seconds), "Verificare telefon".

## 12. Milestones

Stop after each milestone, show what works, and wait for approval.

**M0. Skeleton.** Check for JDK 17 and the Android SDK command-line tools on the Mac; if missing, propose the Homebrew commands and wait for approval before installing. Create the Gradle project and modules, apply the design system to an empty Acasă screen, add the GitHub Actions workflow and `README.md`. Done = `./gradlew assembleDebug` passes locally and in CI, and the APK shows the themed home screen.

**M1. Test bench** (`tools/testbench`, reads `GROQ_API_KEY` from the environment or `secrets.properties`). Commands: `enrich <lesson.md>`, `assess <lesson.md> <retellings.txt>`, `transcribe <audio.m4a>`. Output Markdown tables (idea × retelling status, latency, tokens). Write 2 sample lessons (one Istorie, one Geografie, clearly marked as samples) and 10 sample retellings in a 10-year-old's Romanian with recognition-style errors. Done = it runs against Groq's free plan and the owner reviews the results.

**M2. Core.** Parser, pack builder, safety filter, coverage judge, session engine, router, Groq adapters; unit tests with recorded replies, no network in tests. Done = tests green in CI; a scripted session runs end to end in the test bench.

**M3. Screens + storage.** Room, all screens in section 11 with keyboard input, parent mode with import and review. Done = a full session by typing, surviving the app being killed mid-session.

**M4. Voice.** Recorder, Groq Whisper, Android fallback, TTS, permissions, phone check. Done = a full session by voice on the owner's phone.

**M5. Hardening.** 429 and offline handling everywhere, usage screen, accessibility (TalkBack labels, 130% font size, reduced motion), dark mode, release signing, install guide for a Family Link phone in `README.md`. Done = the definition of done passes.

## 13. Definition of done

- [ ] A lesson `.md` with key ideas imports and is ready for review in under 1 minute.
- [ ] Gap marking agrees with the parent on at least 8 of 10 test retellings.
- [ ] A session stays within 25 LLM calls and 40,000 tokens.
- [ ] A Groq 429 or lost internet never crashes or loses a session.
- [ ] Audio files are deleted after transcription; the child's name and the API key never leave the phone or appear in logs.
- [ ] All screens follow DESIGN_SYSTEM.md, in light and dark mode, at 130% font size.
- [ ] GitHub Actions builds installable APKs on every push to `main`.

## 14. Ask the owner, don't guess

- Anything not covered here or in `docs/architecture-decisions.md`.
- Any new dependency, cloud service, analytics or permission.
- Sending anything to a third party beyond: audio and transcripts to Groq, lesson text to Groq on import.
- Missing design-system assets.
