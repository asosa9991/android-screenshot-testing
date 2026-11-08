#!/usr/bin/env bash
# Prints lock values from the current machine and booted emulator, for emulator.lock.
set -euo pipefail
cd "$(dirname "$0")/../.."
source tooling/env.sh
echo "EMULATOR_VERSION=\"$(emulator -version 2>/dev/null | head -1)\""
echo "EXPECTED_FINGERPRINT=$(adb shell getprop ro.build.fingerprint | tr -d '\r')"
for f in .cache/emu/emulator.zip .cache/emu/sysimg.zip; do
  [[ -f "$f" ]] && echo "# sha256 $f: $(shasum -a 256 "$f" | cut -d' ' -f1)"
done
true
