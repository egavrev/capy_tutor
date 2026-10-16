#!/usr/bin/env bash
# UI tour for CI: installs nothing, drives the installed debug APK through every M3 screen with
# adb, and saves screenshots + logcat into $OUT. Runs on any device reachable with adb.
#
# The app is started with the debug-only extra `capy_demo` (see app/.../demo/Demo.kt): no Groq
# key, a keyword-only stand-in for the model, a sample child "Ana" with PIN 1234.
# Typing uses `adb shell input text`, which only types ASCII, so answers are written without
# diacritics (the keyword matcher folds them anyway).
set -u
OUT=${OUT:-shots}
PKG=com.capytutor
mkdir -p "$OUT"
STEP=0

log() { echo "[tour] $*"; echo "$*" >> "$OUT/TOUR.txt"; }

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  log "shot $1"
}

start() { # start [demo-mode]
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity --es capy_demo "${1:-1}" >/dev/null
  sleep 4
}

dump() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml > "$OUT/.ui.xml"
}

# find "label" -> prints "x y" of the first node whose text or content-desc equals the label
# (or starts with it when the label ends with '*'). Empty when not on screen.
find_node() {
  dump
  python3 - "$OUT/.ui.xml" "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
path, want = sys.argv[1], sys.argv[2]
prefix = want.endswith('*')
w = want[:-1] if prefix else want
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
best = None
for n in root.iter('node'):
    for attr in ('text', 'content-desc'):
        v = (n.get(attr) or '').strip()
        if (v.startswith(w) if prefix else v == w):
            m = re.findall(r'\d+', n.get('bounds', ''))
            if len(m) == 4:
                x1, y1, x2, y2 = map(int, m)
                if x2 > x1 and y2 > y1:
                    best = ((x1 + x2) // 2, (y1 + y2) // 2)
                    break
    if best:
        break
if best:
    print(best[0], best[1])
PY
}

tap() { # tap "label" [wait]
  local xy
  xy=$(find_node "$1")
  if [ -z "$xy" ]; then
    STEP=$((STEP + 1))
    cp "$OUT/.ui.xml" "$OUT/missing_${STEP}.xml" 2>/dev/null
    log "!! not found: '$1' (see missing_${STEP}.xml)"
    return 1
  fi
  adb shell input tap $xy
  sleep "${2:-1.5}"
}

has() { [ -n "$(find_node "$1")" ]; }

type_text() { # ASCII only; typed in short pieces so the IME keeps up
  local rest=$1 piece
  while [ -n "$rest" ]; do
    piece=${rest:0:24}; rest=${rest:24}
    adb shell input text "${piece// /%s}"
    sleep 0.6
  done
  sleep 1
}

pin() { for d in 1 2 3 4; do tap "$d" 0.4; done; sleep 1.5; }

hide_keyboard() { adb shell input keyevent 111; sleep 0.8; } # ESC closes the IME

adb logcat -c
adb shell cmd uimode night no
adb shell settings put system font_scale 1.0

# ---- 0. Upgrade: the previous database version's APK with lessons, then this one over it ------
# (prev.apk is fetched by the workflow: the last build before the Room version changed.)
if [ -f "${PREV_APK:-prev.apk}" ]; then
  adb uninstall $PKG >/dev/null 2>&1
  adb install "${PREV_APK:-prev.apk}" >/dev/null && log "installed the previous APK"
  start 1
  sleep 3
  adb install -r "${APK:-app.apk}" >/dev/null && log "installed this APK over it"
  start 1
  shot 00_upgrade_home
  if has "Apariția omului*"; then log "upgrade kept the lessons"; else log "!! upgrade lost the lessons"; fi
  adb shell pm clear $PKG >/dev/null
else
  adb install -r "${APK:-app.apk}" >/dev/null && log "installed this APK"
fi

# ---- 0b. The start intro: both scenes, frozen at four moments, then one live start -------------
intro_frames() { # intro_frames suffix frame...
  local suffix=$1; shift
  for scene in show museum; do
    for f in "$@"; do
      adb shell am force-stop $PKG
      adb shell am start -W -n $PKG/.MainActivity --es capy_intro "$scene:$f" >/dev/null
      shot "00_intro_${scene}_${f}${suffix}" 2
    done
  done
}
intro_frames "" 0.15 0.45 0.7 1
# CI turns animations off, which skips the intro: turn them on for two live starts (the scenes alternate).
ANIM=$(adb shell settings get global animator_duration_scale | tr -d '\r')
adb shell settings put global animator_duration_scale 1
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity >/dev/null
shot 00_intro_live_a 1.1
sleep 3
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity >/dev/null
shot 00_intro_live_b 1.1
sleep 3
if adb shell pidof $PKG >/dev/null; then log "intro: app still running after two live starts"; else log "!! intro: app not running"; fi
adb shell settings put global animator_duration_scale "${ANIM:-0}"
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/.MainActivity >/dev/null
shot 00_intro_animations_off 1.5
adb shell pm clear $PKG >/dev/null

# ---- 1. First launch: setup for the parent --------------------------------------------------
start fresh
shot 01_setup_welcome
tap "Să începem"
shot 02_setup_child 1
tap "Numele copilului" && type_text "Ana"
tap "Fată"
hide_keyboard
shot 03_setup_child_filled 1
tap "Mai departe"
shot 04_setup_pin 1
pin
shot 05_setup_pin_confirm 1
pin
shot 06_setup_key 1
tap "Mai târziu"
shot 07_home_empty

# ---- 2. Lessons arrive (demo seed: one ready, one to check); retelling by voice, then typing ----
start 1
shot 08_home_lessons
tap "Apariția omului*"
shot 09_lesson_ready
tap "Citesc lecția întâi"
shot 09b_reading
adb shell input swipe 540 1700 540 600 300; sleep 1
shot 09c_reading_more 1
tap "Am citit, hai să povestesc"
shot 10_retelling_mic
tap "Vorbește"
shot 11_mic_explain 1
tap "Permite microfonul"
shot 12_mic_permission_dialog 2
tap "While using the app" || tap "Allow" || tap "Only this time"
sleep 2
shot 13_recording 0
sleep 2
tap "Oprește, am terminat" 3
shot 14_transcribed 1
tap "Trimite"
tap "Scriu cu tastatura"
tap "Scrie ce ții minte…" && type_text "Oamenii faceau unelte din piatra."
tap "Trimite"
hide_keyboard
shot 15_retelling_sent 1
tap "Gata, am terminat" 3
shot 16_coverage 1
tap "Hai să le căutăm împreună"
shot 17_hint_broad
tap "Scrie sau apasă microfonul" && type_text "nu stiu exact"
tap "Trimite" 3
hide_keyboard
shot 18_hint_clue 1

# ---- 3. Kill the app mid-session, reopen: same place -----------------------------------------
log "force-stop in the middle of the hint ladder"
start 1
shot 19_home_resume
tap "Apariția omului*"
shot 20_resumed

# ---- 4. Through the remaining hints with "Nu-mi amintesc", then the test ---------------------
for i in $(seq 1 30); do
  has "Nu-mi amintesc" || break
  tap "Nu-mi amintesc" 1.2
done
shot 21_test_question 1
tap "Scrie sau apasă microfonul" && type_text "in Africa"
tap "Trimite" 3
shot 22_feedback_right 1
tap "Mai departe"
tap "Scrie sau apasă microfonul" && type_text "din lemn"
tap "Trimite" 3
shot 23_feedback_not_yet 1
tap "Mai departe"
for i in $(seq 1 20); do
  if has "Nu știu"; then tap "Nu știu" 1.2; fi
  if has "Mai departe"; then tap "Mai departe" 1.2; else break; fi
done
sleep 3
shot 24_summary
tap "Gata pe azi"
shot 25_done 1
tap "Înapoi acasă"
shot 25b_home_bows 1

# ---- 4b. Recapitulare: a quiz of every type, killed in the middle, then Capy's corner ---------
tap "Recapitulare"
shot 26a_recap
tap "Apariția omului*"
shot 26b_quiz_choice
tap "În Africa"
shot 26c_choice_selected 1
tap "Verifică"
shot 26d_feedback_right 1
tap "Mai departe"
tap "Adevărat"
tap "Verifică"
shot 26e_truefalse_not_yet 1
tap "Mai departe"
log "force-stop in the middle of the quiz"
start 1
tap "Recapitulare"
tap "Apariția omului*"
shot 26f_quiz_resumed_order
tap "Trăiește Lucy*" 0.8
tap "Primele unelte*" 0.8
shot 26g_order_placed 1
tap "Scheletul lui Lucy*" 0.8
tap "Verifică"
shot 26h_order_right 1
tap "Mai departe"
shot 26i_match
tap "fosilă" 0.8
tap "urmă a unei ființe*" 0.8
tap "paleolitic" 0.8
tap "obiect cu care*" 0.8
shot 26j_match_paired 1
tap "epoca veche a pietrei" 0.8
tap "Verifică"
shot 26k_match_not_yet 1
tap "Mai departe"
tap "Din vânătoare*"; tap "Verifică"; tap "Mai departe"
tap "Adevărat"; tap "Verifică"; tap "Mai departe"
tap "În peșteri sau colibe"; tap "Verifică"; tap "Mai departe"
shot 26l_quiz_results 1
tap "Refac testul"
shot 26m_quiz_again 1
adb shell input keyevent 4; sleep 1.5
tap "Colțul lui Capy"
shot 26n_corner
adb shell input swipe 540 1700 540 500 300; sleep 1
shot 26o_corner_shelf 1
tap "Acasă"

# ---- 5. Parent mode ----------------------------------------------------------------------------
tap "Mod părinte"
shot 26_pin_gate 1
tap "9" 0.4; tap "9" 0.4; tap "9" 0.4; tap "9" 1.5
shot 27_pin_wrong 1
pin
shot 28_parent_home
tap "Progres"
shot 28b_progress
tap "Apariția omului*"
shot 28c_lesson_progress
adb shell input swipe 540 1700 540 600 300; sleep 1
shot 28d_lesson_progress_more 1
adb shell input keyevent 4; sleep 1.5
adb shell input keyevent 4; sleep 1.5
tap "Toate lecțiile"
shot 28e_all_lessons
tap "Geografie*"
shot 28f_unit_open 1
tap "Pământul*"
shot 29_review
tap "Vezi și editează tot"
shot 30_review_edit 1
for i in 1 2 3 4 5 6 7 8; do
  has "Vezi întrebările" && break
  adb shell input swipe 540 1900 540 500 300; sleep 0.8
done
tap "Vezi întrebările"
shot 30b_review_quiz 1
adb shell input keyevent 4; sleep 1.5
adb shell input keyevent 4; sleep 1.5
tap "Lecții aprobate"
shot 30c_approved_lessons
adb shell input keyevent 4; sleep 1.5
tap "Setări"
tap "Vocea lui Capy"
shot 31_settings_voice_on
adb shell input keyevent 4; sleep 1.5
tap "Folosire azi"
shot 32_usage
adb shell input keyevent 4; sleep 1.5
tap "Verificare telefon" 8
shot 33_phone_check 0
adb shell input swipe 540 1900 540 600 300; sleep 1
shot 34_phone_check_more 1
adb shell input keyevent 4; sleep 1.5
tap "Lecții aprobate"
tap "Apariția omului*"
adb shell input swipe 540 1900 540 400 300; sleep 1
adb shell input swipe 540 1900 540 400 300; sleep 1
adb shell input swipe 540 1900 540 400 300; sleep 1
adb shell input swipe 540 1900 540 400 300; sleep 1
tap "Istoric"
shot 35_history

# ---- 6. Dark mode and 130% font ----------------------------------------------------------------
adb shell cmd uimode night yes
intro_frames _dark 0.45 1
start 1
shot 36_home_dark
tap "Apariția omului*"
shot 37_lesson_ready_dark
tap "Citesc lecția întâi"
shot 37b_reading_dark
adb shell input keyevent 4; sleep 1.5
tap "Hai să începem"
tap "Scriu cu tastatura"
tap "Scrie ce ții minte…" && type_text "Oamenii traiau in pesteri si faceau focul."
tap "Trimite"
# Kill the app while Capy is thinking: on reopen the same check runs again.
tap "Gata, am terminat" 0.1
log "force-stop while Capy is thinking"
start 1
tap "Apariția omului*" 3
shot 38_coverage_dark_after_kill 1
tap "Hai să le căutăm împreună"
tap "Scrie sau apasă microfonul" && type_text "in Asia"
tap "Trimite" 3
hide_keyboard
shot 39_hint_dark_speaker 1
adb shell input keyevent 4; sleep 1.5
tap "Recapitulare"
shot 39b_recap_dark
tap "Apariția omului*"
tap "În Europa"
tap "Verifică"
shot 39c_quiz_dark_not_yet 1
tap "Mai departe"
tap "Fals"; tap "Verifică"; tap "Mai departe"
shot 39d_order_dark 1
adb shell input keyevent 4; sleep 1.5
tap "Colțul lui Capy"
shot 39e_corner_dark
adb shell cmd uimode night no

adb shell settings put system font_scale 1.3
start 1
shot 40_home_font130
tap "Apariția omului*"
shot 41_session_font130
adb shell input keyevent 4; sleep 1.5
tap "Recapitulare"
tap "Apariția omului*"
shot 42_quiz_font130
adb shell input keyevent 4; sleep 1.5
tap "Colțul lui Capy"
shot 43_corner_font130
adb shell settings put system font_scale 1.0

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/CRASH.txt"
  log "!! the app crashed, see CRASH.txt"
fi
rm -f "$OUT/.ui.xml"
exit 0
