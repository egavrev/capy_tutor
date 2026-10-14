# CLAUDE.md: Capy Tutor

Standing rules for every session in this repository. The full MVP spec is `HANDOFF.md`; the look of every screen is `design-system/DESIGN_SYSTEM.md`; the decisions behind both are `docs/architecture-decisions.md`.

## The project

Android app (Kotlin, Jetpack Compose, Material 3) for one 5th grader in Moldova. She retells a textbook lesson by voice; Capy the capybara finds the key ideas she missed, guides her to recall them with hints, then asks one question per idea. Romanian UI. Family use only, installed as an APK.

- Application id / package: `com.capytutor`
- Repository: `egavrev/capy_tutor` (GitHub Actions builds the APKs; the owner has no Android Studio)
- AI: Groq free plan only. Chat `openai/gpt-oss-120b`, speech-to-text `whisper-large-v3-turbo`. No fallback provider in the MVP. Never the Gemini API.
- Voice output: Android TextToSpeech, Romanian, off by default.

## Rules

1. **Work milestone by milestone** (HANDOFF.md §12). Stop at the end of each, report, and wait for approval.
2. **Ask before** adding a dependency, a permission, a cloud service, or installing anything on the Mac.
3. **Design system first.** Read `design-system/DESIGN_SYSTEM.md` before any UI change. Only theme tokens; never `Color(0x…)` or raw `.dp`/`.sp` outside the theme files. Never red. Right = pink + `checkheart` + words; not yet = Champagne + `sprout` + "Încă nu".
4. **Romanian copy** with ș ț (comma below). Capy says "Încă nu", never "Greșit". No timers or time estimates anywhere.
5. **Secrets:** the Groq key lives only in encrypted app storage (entered in parent mode) and, for the test bench, in the environment or `secrets.properties` (git-ignored). Never commit, log or print a key. Never commit `signing/`.
6. **Privacy:** replace the child's name with "copilul" before any API call; delete audio files right after transcription; send nothing to any service other than Groq.
7. **Survive limits:** every Groq call can return 429 or time out; pause gracefully and keep the session resumable.
8. **Business logic in `:core`** (plain Kotlin), unit-tested with recorded replies; no network in tests.
9. **Prompts** live as files in `:core/src/main/resources/prompts/`; every model reply is validated against a schema.
10. Before finishing a task: `./gradlew test assembleDebug` passes, and the DESIGN_SYSTEM.md checklist is done for UI changes.

## Folder map

| Path | What |
|---|---|
| `HANDOFF.md` | Full MVP spec (v2) |
| `CLAUDE_CODE_PROMPT.md` | The prompt the owner pastes to start |
| `docs/architecture-decisions.md` | The owner's decisions |
| `docs/lesson-extraction-prompt.md` | How lesson files are produced |
| `design-system/` | DESIGN_SYSTEM.md plus the owner's design package (PDF, tokens, res) |
| `lessons/` | Lesson `.md` files: `istorie/`, `geografie/`, `TEMPLATE-lectie.md` |
| `signing/` | Release keystore, git-ignored |
| `app/`, `core/`, `ai/`, `data/`, `tools/testbench/` | Created by you, per HANDOFF.md |
