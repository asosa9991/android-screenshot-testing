#!/usr/bin/env bash
# Local (Apple Silicon) emulator, in one of two modes:
#   (default)  headless, CPU-rendered, wiped on every boot: what screenshot tests need.
#              Results are advisory; CI's pinned x86_64 emulator is the source of truth.
#   --window   a visible window for using the app by hand
# Flags: --reuse keeps a running emulator started in the same mode; --any keeps any running one.
set -euo pipefail
cd "$(dirname "$0")/../.."
source tooling/env.sh

mode=pinned; reuse=false; any=false
for arg in "$@"; do
  case $arg in --window) mode=window ;; --reuse) reuse=true ;; --any) reuse=true; any=true ;; esac
done

LOCAL_IMG=${SHOTS_LOCAL_IMAGE:-system-images/android-36/google_apis_playstore/arm64-v8a}
NAME=shots_local
AVD_HOME=${ANDROID_AVD_HOME:-$HOME/.android/avd}
MARK=.cache/emulator-mode
mkdir -p .cache build
running() { adb devices | grep -qE '^emulator-[0-9]+[[:space:]]+device$'; }

if running; then
  current=$(cat "$MARK" 2>/dev/null || echo unknown)
  if $reuse && { $any || [[ "$current" == "$mode" ]]; }; then
    [[ "$current" == pinned ]] && exec tooling/emulator/configure-device.sh
    exit 0
  fi
  echo "Restarting the emulator in $mode mode (it was running in $current mode)."
  adb emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do running || break; sleep 1; done
fi

[[ -d "$ANDROID_SDK_ROOT/$LOCAL_IMG" ]] || {
  echo "Missing $LOCAL_IMG. Install it with Android Studio's SDK Manager or set SHOTS_LOCAL_IMAGE."; exit 1; }

api=$(echo "$LOCAL_IMG" | sed -E 's#.*android-([^/]+)/.*#\1#')
tag=$(echo "$LOCAL_IMG" | cut -d/ -f3)
mkdir -p "$AVD_HOME/$NAME.avd"
printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-%s\n' "$AVD_HOME/$NAME.avd" "$api" > "$AVD_HOME/$NAME.ini"
sed -e "s#^image.sysdir.1=.*#image.sysdir.1=$LOCAL_IMG/#" \
    -e 's/^abi.type=.*/abi.type=arm64-v8a/' -e 's/^hw.cpu.arch=.*/hw.cpu.arch=arm64/' \
    -e "s/^tag.id=.*/tag.id=$tag/" -e 's/^AvdId=.*/AvdId=shots_local/' \
    tooling/emulator/config.ini > "$AVD_HOME/$NAME.avd/config.ini"

if [[ $mode == pinned ]]; then
  echo "Booting the screenshot emulator (headless, CPU rendering). Local results are advisory."
  flags=(-no-window -no-snapshot -wipe-data -gpu swiftshader_indirect)
else
  echo "Opening the emulator window."
  flags=(-no-snapshot-save -gpu auto)
fi
nohup emulator -avd "$NAME" "${flags[@]}" -no-audio -no-boot-anim -change-locale en-US -timezone Etc/UTC \
  > build/emulator.log 2>&1 &
echo "$mode" > "$MARK"

for _ in $(seq 1 40); do running && break; sleep 3; done
running || { echo "Emulator never appeared. See build/emulator.log"; exit 75; }
for _ in $(seq 1 140); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break; sleep 3
done
[[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] || { echo "Boot timeout"; exit 75; }

if [[ $mode == pinned ]]; then
  nohup adb logcat -v threadtime > build/logcat.txt 2>&1 &
  tooling/emulator/configure-device.sh
else
  adb shell wm dismiss-keyguard
  echo "Emulator ready."
fi
