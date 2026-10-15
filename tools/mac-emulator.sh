#!/usr/bin/env bash
# Runs Capy Tutore on an Android 13 emulator on an Apple Silicon Mac, without Android Studio.
#   bash tools/mac-emulator.sh          # the real app (first-launch setup, your Groq key)
#   bash tools/mac-emulator.sh demo     # demo child "Ana", PIN 1234, sample lessons, no Groq
# First run installs about 6-8 GB (Homebrew's Android command-line tools, the emulator and one
# system image) and takes a while. Later runs only start the emulator and reinstall the APK.
set -eo pipefail

export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
IMG="system-images;android-33;google_apis_playstore;arm64-v8a"
HERE=$(cd "$(dirname "$0")/.." && pwd)
APK="$HERE/capy-tutor-debug.apk"

step() { printf '\n== %s\n' "$*"; }

if ! command -v sdkmanager >/dev/null 2>&1; then
  step "Installing the Android command-line tools (Homebrew)"
  brew install --cask android-commandlinetools
fi

step "Accepting the Android SDK licences"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

AVDM="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
ADB="$ANDROID_HOME/platform-tools/adb"
EMU="$ANDROID_HOME/emulator/emulator"

if [ ! -x "$AVDM" ] || [ ! -x "$ADB" ] || [ ! -x "$EMU" ] || [ ! -d "$ANDROID_HOME/system-images/android-33/google_apis_playstore/arm64-v8a" ]; then
  step "Downloading the emulator and the Android 13 image (a few GB, once)"
  sdkmanager "cmdline-tools;latest" "platform-tools" "emulator" "$IMG"
fi

# Homebrew's own avdmanager is too old for this image ("Could not load devices ... devices.xml").
if ! "$AVDM" list avd -c 2>/dev/null | grep -qx capy; then
  step "Creating the emulator 'capy' (Pixel 6, Android 13)"
  echo no | "$AVDM" create avd -n capy -d pixel_6 -k "$IMG"
fi

if ! "$ADB" devices | grep -q '^emulator-'; then
  step "Starting the emulator (its window opens in a moment)"
  # -allow-host-audio: the Mac's microphone reaches the emulator, so Capy can hear you.
  nohup "$EMU" -avd capy -allow-host-audio >/tmp/capy-emulator.log 2>&1 &
fi

step "Waiting for Android to finish starting"
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done

step "Installing Capy Tutore"
"$ADB" install -r "$APK"

# Sample lessons in Downloads, for "Adaugă o lecție" in parent mode.
"$ADB" push "$HERE/lessons/istorie/SAMPLE-aparitia-omului.md" /sdcard/Download/ >/dev/null
"$ADB" push "$HERE/lessons/geografie/SAMPLE-pamantul.md" /sdcard/Download/ >/dev/null

step "Opening the app"
if [ "${1:-}" = "demo" ]; then
  "$ADB" shell am start -n com.capytutor/.MainActivity --es capy_demo 1 >/dev/null
else
  "$ADB" shell am start -n com.capytutor/.MainActivity >/dev/null
fi
echo "Done. Capy Tutore is open in the emulator window."
