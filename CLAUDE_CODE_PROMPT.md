# Prompt to paste into Claude Code

Open a terminal in this folder (`~/Documents/Development/capy_tutor`), run `claude`, then paste:

---

You are building the first MVP of **Capy Tutor**, an Android app for my daughter. Start by reading, in this order:

1. `CLAUDE.md`: the standing rules for this repo.
2. `HANDOFF.md`: the full MVP specification.
3. `docs/architecture-decisions.md`: my decisions (they win if anything conflicts).
4. `design-system/DESIGN_SYSTEM.md` and the list of files in `design-system/`: how every screen must look.

Then:

- Tell me in a few lines what you understood, anything that contradicts itself, and anything missing (for example design-system assets that are not in `design-system/` yet).
- Do **milestone M0** from HANDOFF.md §12: check the Mac for JDK 17 and the Android SDK command-line tools (propose Homebrew commands and wait for my OK before installing anything), create the Gradle project with the modules `:app`, `:core`, `:ai`, `:data`, apply the design system to an empty home screen, add `.github/workflows/android.yml` (tests + debug/release APKs, APKs pushed to a `builds` branch, release signing from secrets `CAPY_TUTOR_KEYSTORE_B64` and `CAPY_TUTOR_KEYSTORE_PASSWORD`), and a `README.md`.
- Initialise git, commit, and tell me the exact commands to create the GitHub repo `egavrev/capy_tutor` and push (or run them if `gh` is installed and logged in, after asking).

Stop after M0 and wait for my approval before M1. Ask before adding any dependency not named in HANDOFF.md.

---

## Before you paste it

- [ ] Put the full design package (PDF, `tokens.json`, `res/` files, icons, Capy PNGs, floral background) into `design-system/`, next to `DESIGN_SYSTEM.md`.
- [ ] Keep your Groq API key ready, but **don't paste it into the chat**. For the test bench (M1), copy `secrets.properties.example` to `secrets.properties` and put the key there, or run `export GROQ_API_KEY=...` in the terminal.
- [ ] Optional: copy a real Istorie or Geografie lesson into `lessons/istorie/` or `lessons/geografie/` (made with `docs/lesson-extraction-prompt.md`).
