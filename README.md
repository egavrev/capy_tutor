# Capy Tutor

A calm Android study buddy: Vali retells a lesson by voice, Capy finds what she missed, helps her remember it, and asks one question per key idea. Kotlin, Jetpack Compose, Groq's free AI. UI name: *Capy Tutore*.

**Status:** M3 (screens, storage, parent mode) done: a whole session works by typing and survives the app being killed. M4 (voice) is built and passes the emulator tour; it is done once a full session by voice works on Vali's phone. Next: M5 (hardening, release signing, install guide). See `HANDOFF.md` §12 for the milestones.

## Using the app

1. **First launch (parent):** the child's name, how Capy writes about her (fată / băiat / nu spun), a 4-digit PIN for parent mode, the Groq API key (optional now, also in Setări). The name never leaves the phone; Groq only sees "copilul".
2. **Add a lesson (parent):** tap the lock on Acasă, type the PIN, *Adaugă o lecție (.md)*. Or share the `.md` file to Capy Tutore from Files, Drive or a chat app. Capy reads it once with Groq and writes the hints and questions.
3. **Review it:** open the lesson, check each idea (*Vezi și editează tot*), especially the test question, then *Lecția e gata*. Only ready lessons can be opened by the child.
4. **The child:** opens the lesson, *Hai să începem*, taps the mic and retells (or *Scriu cu tastatura*). The transcript appears in the box, where she can fix it, then *Gata, am terminat*. Hints, the test and the summary follow.
5. **Afterwards:** parent mode → the lesson → *Istoric*: results per idea, and any "Cred că am dreptate" to accept or reject.

Voice: recordings go to Groq Whisper and are deleted as soon as the text comes back. When Groq is busy (limit reached) or there is no internet, Capy uses the phone's own speech recognition if it has Romanian, otherwise she asks for typing. Capy's voice (reading her messages aloud) is off by default: Setări → *Vocea lui Capy*. Parent mode → *Verificare telefon* shows what this phone can do (microphone, Groq, Romanian voice, Romanian recognition).

Everything is saved after every step: closing the app, a call or the battery dying never loses a session; it opens where it stopped.

## Getting the APK

No Android Studio is needed. Every push to `main` is built by GitHub Actions (`.github/workflows/android.yml`):

1. unit tests (`./gradlew test`),
2. the debug APK, plus the signed release APK once the signing secrets exist (see below),
3. a tour of every screen on an Android 13 emulator (the same Android version as Vali's Galaxy Note 20 Ultra), driven by `tools/screenshots.sh` with a demo child "Ana" and no Groq: setup, a whole session by voice and keyboard, killing the app mid-session, parent mode, dark mode and 130% font size. If a build fails, the compiler errors are on the [`ci-logs`](../../tree/ci-logs) branch.

The latest APKs are on the [`builds`](../../tree/builds) branch:

- `capy-tutor-debug.apk`: debug build, for testing now.
- `capy-tutor.apk`: release build, the one to keep on the phone (appears once the signing secrets are set).

The screenshots are on the [`screenshots`](../../tree/screenshots) branch (`TOUR.txt` lists every step).

**Trying it on the Mac's Android emulator (optional, about 6–8 GB, no Android Studio):** `bash tools/mac-emulator.sh` (or `… demo`) does all of the steps below and opens the app. By hand:
```
brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
sdkmanager --licenses
sdkmanager "cmdline-tools;latest" "platform-tools" "emulator" "system-images;android-33;google_apis_playstore;arm64-v8a"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
avdmanager create avd -n capy -d pixel_6 -k "system-images;android-33;google_apis_playstore;arm64-v8a"
emulator -avd capy &
adb install -r capy-tutor-debug.apk
```
Use the `avdmanager` from `cmdline-tools/latest` (as above): Homebrew's own copy is too old for the Android 13 image and fails with "Could not load devices … devices.xml". Put the two `export` lines in `~/.zshrc`.

- Demo without a Groq key (child "Ana", PIN 1234, the two sample lessons): `adb shell am start -n com.capytutor/.MainActivity --es capy_demo 1`
- A lesson for a real import: `adb push lessons/istorie/SAMPLE-aparitia-omului.md /sdcard/Download/`
- Microphone: the emulator's *⋯ → Microphone → Virtual microphone uses host audio input* (off again at every start).

The real check for voice is Vali's phone.

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
| `tools/screenshots.sh` | The CI tour of every screen (adb, demo mode) |
| `tools/testbench/` | Command-line test bench against Groq, with sample retellings in `samples/` |
| `design-system/` | `DESIGN_SYSTEM.md`, tokens and the design package (source of truth for the look) |
| `lessons/` | Lesson Markdown files: `istorie/`, `geografie/`, `TEMPLATE-lectie.md` |
| `docs/` | Architecture decisions, the lesson extraction prompt, test bench results |
| `CLAUDE.md`, `HANDOFF.md` | Rules and full MVP spec for Claude |
| `signing/` | Release keystore (local only, never committed) |

## Secrets

- The Groq API key is typed in parent mode on the phone (or at first launch) and stored encrypted with the Android Keystore. Only its last 4 characters are ever shown.
- For the test bench, copy `secrets.properties.example` to `secrets.properties` (git-ignored) and put the key there, or `export GROQ_API_KEY=...`. Never paste it into a chat.

## Links

- Google Drive: Capy Tutor folder (architecture doc, prompts, lessons, builds)
- Full write-up with all 21 epics: https://claude.ai/code/artifact/bb52655c-45a5-45fd-b568-fa1c6965fd97
