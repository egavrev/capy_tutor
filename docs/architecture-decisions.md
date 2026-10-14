# Capy Tutor: architecture decisions

Taken by Eugeniu on 14 October 2026 in "Capy Tutor – MVP architecture and decisions" (Google Drive › Capy Tutor › 01 Architecture). Where this file and HANDOFF.md disagree, this file wins.

## Fixed from the start

- **Main LLM: Groq free plan.** API key registered; entered once in parent mode on the phone.
- **Lesson input: Markdown files**, one per lesson. No camera in the MVP.
- **All AI behind three interfaces** (`LlmProvider`, `SpeechToText`, `TextToSpeech`).
- **No Gemini API in the app** (its terms exclude apps used by under-18s).

## Decisions

| # | Question | Decision | What it means for the build |
|---|---|---|---|
| 1 | Fallback LLM | **No fallback in the MVP** | Router keeps one entry per call; on 429 the session pauses and resumes later |
| 2 | Groq model for checking | **openai/gpt-oss-120b** | Used for all chat calls; id in configuration |
| 3 | Vali's phone | **Unknown, check during setup** | Parent mode gets a "Verificare telefon" screen |
| 4 | Speech-to-text | **Groq Whisper by default** | Record on the phone, send to `whisper-large-v3-turbo`, delete the audio after; Android recogniser only as offline / 429 fallback |
| 5 | Key ideas | **Parent writes `## Idei cheie` in every file** | Import rejects files without them; no automatic extraction |
| 6 | Closing check | **One question per key idea** | `checkQuestion` generated at import; keyword pass first, LLM only if unclear |
| 7 | Capy's voice | **Off by default**, speaker button on each bubble | Android TTS ro-RO |
| 8 | Groq API key | **Typed in parent mode, stored encrypted** | Keystore-backed storage; never in the APK |
| 9 | Build | **GitHub Actions, no Android Studio**; repo **egavrev/capy_tutor** | Same CI pattern as Capy Pomodoro |
| 10 | Who uses it | **Family only**, APK on Vali's Family Link phone | No Play Store rules to meet |
| 11 | Session length | **No target** | No timers, no time estimates |
| 12 | First subjects | **Istorie, Geografie** | Sample lessons and first tests in these two |

## Known trade-offs

- **Audio goes to Groq (decision 4).** Better Romanian accuracy for a child's voice, at the cost of sending recordings to a third party. Mitigations: no name in the audio metadata or prompt, files deleted right after transcription, the parent can switch to on-device recognition later.
- **No fallback LLM (decision 1).** Simpler; a Groq outage or daily limit pauses the session. Groq's free plan allows about 1,000 chat requests and 200,000 tokens a day, far above one child's use.
