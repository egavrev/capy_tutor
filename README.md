# Capy Tutor

A calm Android study buddy: Vali retells a lesson by voice, Capy finds what she missed, helps her remember it, and asks one question per key idea. Kotlin, Jetpack Compose, Groq's free AI. UI name: *Capy Tutore*.

**Status:** M3 (screens, storage, parent mode) done: a whole session works by typing and survives the app being killed. M4 (voice) is built and passes the emulator tour; it is done once a full session by voice works on Vali's phone. Release 2: E11 (quiz) and E12 (Capy's corner) are built; MVP6: E14 (reading mode) and E15 (parent progress view) are built. M5 (hardening, release signing, Family Link guide) is partly done. See `HANDOFF.md` §12 for the milestones.

## Using the app

0. **The start:** a 2-second intro, a different scene each time the app starts: Capy hosts the question show (light sticks with "?" turn into hearts, the ramen steam becomes a globe) or Capy's night shift at the little museum (a flashlight finds Capy, the lamp comes on). A tap skips it; it does not play when the phone has animations turned off. Design brief: the "Capy Tutore – splash screen brief" doc, ideas 3 and 4.
1. **First launch (parent):** the child's name, how Capy writes about her (fată / băiat / nu spun), a 4-digit PIN for parent mode, the Groq API key (optional now, also in Setări). The name never leaves the phone; Groq only sees "copilul".
2. **Add a lesson (parent):** tap the lock on Acasă, type the PIN, *Adaugă o lecție (.md)*. Or share the `.md` file to Capy Tutore from Files, Drive or a chat app. Capy reads it once with Groq and writes the hints and questions.
3. **Review it:** open the lesson, check each idea (*Vezi și editează tot*), especially the test question, then *Lecția e gata*. Only ready lessons can be opened by the child. Too many lessons? Parent mode → *Toate lecțiile* → *Șterge lecții*: tick lessons, a whole unit or everything shown (use the filter or search first), then *Șterge* and a second tap. Their history, progress and quiz go too; bows already earned stay. One lesson can also be deleted from its own page.
4. **The child:** opens the lesson; *Citesc lecția întâi* shows the lesson as a clean page, the sentence of each key idea highlighted (soft pink and bold), with *Ascultă lecția* or a speaker per paragraph to hear it in Romanian (on the phone, nothing is sent); *Am citit, hai să povestesc* goes on to the retelling. Or straight to *Hai să începem*, taps the mic and retells (or *Scriu cu tastatura*). The transcript appears in the box, where she can fix it, then *Gata, am terminat*. Hints, the test and the summary follow. From any step, the book icon at the top (*Înapoi la lecție*) starts the lesson over: after *Da, citesc lecția* she reads the lesson, then retells, finds the ideas with Capy and takes the test again. That unfinished attempt is dropped (it never counted for progress or bows); *Rămân aici* keeps her where she was. After the summary the icon only shows the lesson text.
5. **Afterwards:** parent mode → *Progres*: every lesson she worked on (ideas she knows, ideas to practise, last date) and one dated history of lessons and quizzes; tap a lesson to see each key idea as *Știe* / *De exersat* / *Neverificată*, with a dated mark for every attempt. An idea is known when her latest try was right and at most one of her last three was "încă nu". The lesson → *Istoric* still has each session in detail, with any "Cred că am dreptate" to accept or reject.
6. **Recapitulare (quiz):** the second tab. Every ready lesson with a quiz is listed; a quiz has 5–10 questions of four kinds (alege răspunsul, adevărat/fals, pune în ordine, potrivește perechile), answered by tapping and graded on the phone, with no timer and *Refac testul* at the end. Quizzes come from `<lesson>.quiz.json` files next to the lessons (see `docs/quiz-writing-prompt.md` and `tools/quiz-check.py`), or Capy makes one with Groq (*Pregătește quiz-urile* in parent mode). Parent mode → the lesson shows the quiz with its answers.
7. **Colțul lui Capy:** the third tab. Each finished lesson earns 1–3 bows (fundițe): 1 for finishing, 2 from half the test right, 3 from 80 %. A lesson keeps its best result, so nothing is ever lost and there are no streaks. Things appear in Capy's corner as the bows add up (cushion at 3, cup 8, plant 15, lamp 25, shelf 40, window 60), and every finished lesson sits on the bow shelf.

Voice: recordings go to Groq Whisper and are deleted as soon as the text comes back. When Groq is busy (limit reached) or there is no internet, Capy uses the phone's own speech recognition if it has Romanian, otherwise she asks for typing. Capy's voice (reading her messages aloud) is off by default: Setări → *Vocea lui Capy*. Parent mode → *Verificare telefon* shows what this phone can do (microphone, Groq, Romanian voice, Romanian recognition).

Everything is saved after every step: closing the app, a call or the battery dying never loses a session; it opens where it stopped.

## Getting the APK

No Android Studio is needed. Every push to `main` is built by GitHub Actions (`.github/workflows/android.yml`):

1. unit tests (`./gradlew test`),
2. the debug APK, plus the signed release APK once the signing secrets exist (see below),
3. a tour of every screen on an Android 13 emulator (the same Android version as Vali's Galaxy Note 20 Ultra), driven by `tools/screenshots.sh` with a demo child "Ana" and no Groq: an upgrade from the previous database version, setup, a whole session by voice and keyboard, killing the app mid-session, a quiz of every type (killed in the middle too), Capy's corner, parent mode, dark mode and 130% font size. If a build fails, the compiler errors are on the [`ci-logs`](../../tree/ci-logs) branch.

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

Family Link blocks installs from unknown sources, so it goes over USB with adb, as for Capy Pomodoro:

1. In **Family Link**, open Vali's device settings and allow **Developer options**.
2. On her phone: *Settings → About phone → Software information*, tap **Build number** 7 times; then *Settings → Developer options*, turn on **USB debugging**.
3. Connect the phone to the Mac and run `bash tools/phone-install.sh`. Accept "Allow USB debugging?" on the phone (tick *Always allow*) and run it again if it stopped there. It installs the APK, copies the lessons zip to *Downloads* and opens the app.
4. On the phone: first-run setup, the Groq key in parent mode, then *Adaugă lecții* → the zip → review and mark lessons ready. *Verificare telefon* checks the mic and the Romanian voice.
5. Turn USB debugging off and block Developer options again in Family Link. Optionally give Capy Tutore **Unlimited time** (Family Link → Controls → App limits).

Updates: run `tools/phone-install.sh` again; lessons and progress are kept (the database upgrades itself). Uninstalling removes everything: `adb -d uninstall com.capytutor`.

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
