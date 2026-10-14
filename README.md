# Capy Tutor

A calm Android study buddy: Vali retells a lesson by voice, Capy finds what she missed, helps her remember it, and asks one question per key idea. Kotlin, Jetpack Compose, Groq's free AI. UI name: *Capy Tutore*.

**Status:** milestone M2 (core). The whole session (retelling, hints, test, summary) runs in the Terminal with `play`; the phone app still shows only the themed Acasă screen until M3. See `HANDOFF.md` §12 for the milestones.

## Getting the APK

No Android Studio is needed. Every push to `main` is built by GitHub Actions (`.github/workflows/android.yml`):

1. unit tests (`./gradlew test`),
2. the debug APK, plus the signed release APK once the signing secrets exist (see below),
3. screenshots on an Android 13 emulator (the same Android version as Vali's Galaxy Note 20 Ultra): light mode, dark mode and 130% font size.

The latest APKs are on the [`builds`](../../tree/builds) branch:

- `capy-tutor-debug.apk`: debug build, for testing now.
- `capy-tutor.apk`: release build, the one to keep on the phone (appears once the signing secrets are set).

The screenshots are on the [`screenshots`](../../tree/screenshots) branch.

To build the app on the Mac anyway (optional): Java 17 (see the test bench section) plus the Android SDK command-line tools with `ANDROID_HOME` set, then `./gradlew test assembleDebug`.

## Test bench (milestones M1–M2)

`tools/testbench` runs the real prompts against Groq's free plan with the sample lessons (`lessons/**/SAMPLE-*.md`) and sample retellings (`tools/testbench/samples/`), and writes Markdown reports: the generated keywords, hints and questions per idea, each retelling's status per idea next to the parent's mark, latency and tokens.

**On GitHub (no setup on the Mac):** add the `GROQ_API_KEY` repository secret once, then *Actions → Test bench (Groq) → Run workflow*. Reports land on the [`testbench`](../../tree/testbench) branch (`SUMMARY.md` first).

**On the Mac (needs only Java 17, no Android SDK):**

1. Install Java 17 once, and register it with macOS (Homebrew does not do that by itself):
   ```
   brew install openjdk@17
   sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
   java -version          # should print: openjdk version "17..."
   ```
2. Put the key in `secrets.properties` in this folder (or `export GROQ_API_KEY=...`).
3. From this folder:
   ```
   ./gradlew :testbench:run --args="all"                       # everything, reports in tools/testbench/out/
   ./gradlew :testbench:run --args="enrich lessons/istorie/SAMPLE-aparitia-omului.md"
   ./gradlew :testbench:run --args="assess <lesson.md> <retellings.md>"
   ./gradlew :testbench:run --args="transcribe recording.m4a --lesson <lesson.md>"
   ```
   **Try a whole session yourself, typing as Vali** (Enter gol = butonul principal, `/gata`, `/nustiu`, `/dreptate`, `/stop`):
   ```
   ./gradlew -q --console=plain :testbench:run --args="play lessons/istorie/SAMPLE-aparitia-omului.md"
   ./gradlew -q --console=plain :testbench:run --args="play lessons/istorie/SAMPLE-aparitia-omului.md --resume"
   ```
   Scripted sessions (a pretend child, from `tools/testbench/samples/*.session.md`) write the whole conversation to a report:
   ```
   ./gradlew :testbench:run --args="sessions"
   ```
   The first run downloads Gradle and the libraries (a few minutes). Without the Android SDK, Gradle says so and builds only `core`, `ai` and `testbench`; the app itself is built on GitHub Actions.

Enrichment is cached per lesson (`--fresh` regenerates it). Only sample texts go to Groq; the key is never printed. Recordings of Vali's voice stay on the Mac: run `transcribe` there, never in the public repo.

## Release signing

Updates install over the old version only if every APK is signed with the same key. The key is created in milestone M5 and lives in `signing/` in your local folder (git-ignored; **back it up**). CI reads it from two repository secrets:

| Secret | Value |
|---|---|
| `CAPY_TUTOR_KEYSTORE_B64` | the keystore file, base64-encoded |
| `CAPY_TUTOR_KEYSTORE_PASSWORD` | its password |

Add them under GitHub → repo **Settings → Secrets and variables → Actions → New repository secret**. Without them, CI builds the debug APK only.

## Installing on Vali's phone

Same steps as Capy Pomodoro (email or USB, then "Install anyway"; adb if Family Link blocks unknown apps). The full Family Link guide is added in M5.

## Project layout

| Path | What |
|---|---|
| `app/` | Android app: Compose screens, navigation, theme (`ui/theme/`), resources from the design system |
| `core/` | Plain Kotlin: lesson parser, session engine, coverage judge, prompts (`src/main/resources/prompts/`) |
| `ai/` | Plain Kotlin: `LlmProvider`, `SpeechToText`, `TextToSpeech`, provider router, Groq adapters |
| `data/` | Android library: Room database, lesson file store, encrypted settings |
| `tools/screenshots.sh` | Screenshot script used by CI |
| `tools/testbench/` | Command-line test bench against Groq, with sample retellings in `samples/` |
| `design-system/` | `DESIGN_SYSTEM.md`, tokens and the design package (source of truth for the look) |
| `lessons/` | Lesson Markdown files: `istorie/`, `geografie/`, `TEMPLATE-lectie.md` |
| `docs/` | Architecture decisions, the lesson extraction prompt, test bench results |
| `CLAUDE.md`, `HANDOFF.md` | Rules and full MVP spec for Claude |
| `signing/` | Release keystore (local only, never committed) |

## Secrets

- The Groq API key is typed in parent mode on the phone and stored encrypted (from M3).
- For the test bench, copy `secrets.properties.example` to `secrets.properties` (git-ignored) and put the key there, or `export GROQ_API_KEY=...`. Never paste it into a chat.

## Links

- Google Drive: Capy Tutor folder (architecture doc, prompts, lessons, builds)
- Full write-up with all 21 epics: https://claude.ai/code/artifact/bb52655c-45a5-45fd-b568-fa1c6965fd97
