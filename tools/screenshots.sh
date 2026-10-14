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

type_text() { # ASCII only
  local t=${1// /%s}
  adb shell input text "$t"
  sleep 1
}

pin() { for d in 1 2 3 4; do tap "$d" 0.4; done; sleep 1.5; }

hide_keyboard() { adb shell input keyevent 111; sleep 0.8; } # ESC closes the IME

adb logcat -c
adb shell cmd uimode night no
adb shell settings put system font_scale 1.0

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

# ---- 2. Lessons arrive (demo seed: one ready, one to check) ---------------------------------
start 1
shot 08_home_lessons
tap "Apariția omului*"
shot 09_lesson_ready
tap "Hai să începem"
shot 10_retelling
tap "Scrie ce ții minte…" && type_text "Primii oameni au trait in Africa. Lucy a fost gasita in Etiopia. Oamenii faceau unelte din piatra."
tap "Trimite"
hide_keyboard
shot 11_retelling_typed 1
tap "Gata, am terminat" 3
shot 12_coverage 1
tap "Hai să le căutăm împreună"
shot 13_hint_broad
tap "Scrie aici…" && type_text "nu stiu exact"
tap "Trimite" 3
hide_keyboard
shot 14_hint_clue 1

# ---- 3. Kill the app mid-session, reopen: same place -----------------------------------------
log "force-stop in the middle of the hint ladder"
start 1
shot 15_home_resume
tap "Apariția omului*"
shot 16_resumed

# ---- 4. Through the remaining hints with "Nu-mi amintesc", then the test ---------------------
for i in $(seq 1 30); do
  has "Nu-mi amintesc" || break
  tap "Nu-mi amintesc" 1.2
done
shot 17_test_question 1
tap "Scrie aici…" && type_text "in Africa"
tap "Trimite" 3
shot 18_feedback_right 1
tap "Mai departe"
tap "Scrie aici…" && type_text "din lemn"
tap "Trimite" 3
shot 19_feedback_not_yet 1
tap "Mai departe"
for i in $(seq 1 20); do
  if has "Nu știu"; then tap "Nu știu" 1.2; fi
  if has "Mai departe"; then tap "Mai departe" 1.2; else break; fi
done
sleep 3
shot 20_summary
tap "Gata pe azi"
shot 21_done 1
tap "Înapoi acasă"

# ---- 5. Parent mode ----------------------------------------------------------------------------
tap "Mod părinte"
shot 22_pin_gate 1
tap "9" 0.4; tap "9" 0.4; tap "9" 0.4; tap "9" 1.5
shot 23_pin_wrong 1
pin
shot 24_parent_home
tap "Pământul*"
shot 25_review
tap "Vezi și editează tot"
shot 26_review_edit 1
adb shell input keyevent 4; sleep 1.5
tap "Setări"
shot 27_settings
adb shell input keyevent 4; sleep 1.5
tap "Folosire azi"
shot 28_usage
adb shell input keyevent 4; sleep 1.5
tap "Apariția omului*"
adb shell input swipe 540 1900 540 400 300; sleep 1
adb shell input swipe 540 1900 540 400 300; sleep 1
adb shell input swipe 540 1900 540 400 300; sleep 1
adb shell input swipe 540 1900 540 400 300; sleep 1
tap "Istoric"
shot 29_history

# ---- 6. Dark mode and 130% font ----------------------------------------------------------------
adb shell cmd uimode night yes
start 1
shot 30_home_dark
tap "Apariția omului*"
shot 31_lesson_ready_dark
tap "Hai să începem"
tap "Scrie ce ții minte…" && type_text "Oamenii traiau in pesteri si faceau focul."
tap "Trimite"
tap "Gata, am terminat" 3
shot 32_coverage_dark 1
tap "Hai să le căutăm împreună"
tap "Scrie aici…" && type_text "in Asia"
tap "Trimite" 3
hide_keyboard
shot 33_hint_dark 1
adb shell cmd uimode night no

adb shell settings put system font_scale 1.3
start 1
shot 34_home_font130
tap "Apariția omului*"
shot 35_session_font130
adb shell settings put system font_scale 1.0

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/CRASH.txt"
  log "!! the app crashed, see CRASH.txt"
fi
rm -f "$OUT/.ui.xml"
exit 0
