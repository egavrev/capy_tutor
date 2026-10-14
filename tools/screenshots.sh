#!/usr/bin/env bash
# Opens the installed app and captures screenshots + logs into $OUT.
# Used by the "screenshots" CI job; runs on any device reachable with adb.
set -u
OUT=${OUT:-shots}
PKG=com.capytutor
mkdir -p "$OUT"

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
restart() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity >/dev/null; }

adb logcat -c

adb shell cmd uimode night no
restart;                                         shot 01_home_light 6

adb shell cmd uimode night yes
restart;                                         shot 02_home_dark 5

adb shell cmd uimode night no
adb shell settings put system font_scale 1.3
restart;                                         shot 03_home_font130 5
adb shell settings put system font_scale 1.0

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" > "$OUT/CRASH.txt"
  echo "!! the app crashed, see CRASH.txt"
fi
exit 0
