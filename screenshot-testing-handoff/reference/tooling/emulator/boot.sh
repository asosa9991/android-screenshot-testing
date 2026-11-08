#!/usr/bin/env bash
# Boots the one pinned Lane B emulator. Same script for CI and local.
#   --reuse   keep an emulator that is already running (just re-apply device settings)
# Exit 75 = infrastructure failure (CI retries once). Exit 1 = drift (fails immediately).
set -euo pipefail
cd "$(dirname "$0")/../.."
source tooling/env.sh
source tooling/emulator/emulator.lock

if [[ "$(uname -m)" == "arm64" ]]; then
  exec tooling/emulator/boot-local.sh "$@"
fi

running() { adb devices | grep -qE '^emulator-[0-9]+[[:space:]]+device$'; }
# CI: only the pinned headless mode exists; --window and --any are local conveniences.
if [[ " $* " == *" --reuse "* || " $* " == *" --any "* ]] && running; then exec tooling/emulator/configure-device.sh; fi

grep -q REPLACE tooling/emulator/emulator.lock && {
  echo "::error::tooling/emulator/emulator.lock is not filled in. See docs/testing.md, 'Pinning the emulator'."; exit 1; }

SDK=$ANDROID_SDK_ROOT; CACHE=.cache/emu; mkdir -p "$CACHE" build
fetch() { [[ -f "$3" ]] || curl -fsSL "$1" -o "$3"; echo "$2  $3" | sha256sum -c - >/dev/null; }
fetch "$EMULATOR_URL" "$EMULATOR_SHA256" "$CACHE/emulator.zip"
fetch "$SYSIMG_URL" "$SYSIMG_SHA256" "$CACHE/sysimg.zip"
rm -rf "$SDK/emulator" && unzip -q "$CACHE/emulator.zip" -d "$SDK"
IMG="$SDK/system-images/android-$API/default"; rm -rf "$IMG" && mkdir -p "$IMG"
unzip -q "$CACHE/sysimg.zip" -d "$IMG"
actual_version=$("$SDK/emulator/emulator" -version 2>/dev/null | head -1)
[[ "$actual_version" == "$EMULATOR_VERSION" ]] || { echo "::error::emulator version drift: $actual_version"; exit 1; }

# The AVD is defined by files in git, not by avdmanager defaults.
AVD="$HOME/.android/avd"; mkdir -p "$AVD/shots.avd"
printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-%s\n' "$AVD/shots.avd" "$API" > "$AVD/shots.ini"
cp tooling/emulator/config.ini "$AVD/shots.avd/config.ini"

nohup "$SDK/emulator/emulator" -avd shots -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
  -gpu swiftshader_indirect -change-locale en-US -timezone Etc/UTC > build/emulator.log 2>&1 &
timeout 120 adb wait-for-device || { echo "::error::emulator never appeared"; exit 75; }
timeout 420 bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == "1" ]]; do sleep 3; done' \
  || { echo "::error::boot timeout"; exit 75; }
nohup adb logcat -v threadtime > build/logcat.txt 2>&1 &

tooling/emulator/configure-device.sh
actual=$(adb shell getprop ro.build.fingerprint | tr -d '\r')
[[ "$actual" == "$EXPECTED_FINGERPRINT" ]] || { echo "::error::image drift: $actual"; exit 1; }
