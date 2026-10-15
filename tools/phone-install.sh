#!/usr/bin/env bash
# Installs Capy Tutore on a phone connected by USB (USB debugging on), like Capy Pomodoro.
#   bash tools/phone-install.sh
# Uses the adb that tools/mac-emulator.sh installed. Copies the lessons zip to the phone's
# Downloads, for "Adaugă lecții" in parent mode. Works while the emulator is running too (-d = USB).
set -eo pipefail

HERE=$(cd "$(dirname "$0")/.." && pwd)
APK="$HERE/capy-tutor-debug.apk"
ADB="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}/platform-tools/adb"
[ -x "$ADB" ] || ADB=$(command -v adb || true)
if [ -z "$ADB" ] || [ ! -x "$ADB" ]; then
  echo "adb not found. Run tools/mac-emulator.sh once, or: brew install android-platform-tools"
  exit 1
fi

state=$("$ADB" -d get-state 2>&1 || true)
case "$state" in
  device) ;;
  *unauthorized*) echo "On the phone, accept \"Allow USB debugging?\" (tick \"Always allow\"), then run this again."; exit 1 ;;
  *) echo "No phone found over USB. Check the cable, and that USB debugging is on (Settings > Developer options)."; exit 1 ;;
esac
echo "== Phone: $("$ADB" -d shell getprop ro.product.manufacturer | tr -d '\r') $("$ADB" -d shell getprop ro.product.model | tr -d '\r')"

echo "== Installing Capy Tutore"
if ! out=$("$ADB" -d install -r "$APK" 2>&1); then
  echo "$out"
  if echo "$out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    echo "The app on the phone has a different signature. Uninstall Capy Tutore on the phone (this clears its data), then run this again."
  fi
  exit 1
fi

echo "== Copying lessons to Downloads"
for z in "$HERE"/*.zip; do
  [ -e "$z" ] && "$ADB" -d push "$z" /sdcard/Download/ >/dev/null && echo "   $(basename "$z")"
done

"$ADB" -d shell am start -n com.capytutor/.MainActivity >/dev/null
echo "Done. Capy Tutore is open on the phone. Turn USB debugging off when you've finished."
