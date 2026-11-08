#!/usr/bin/env bash
# Puts a booted emulator into the state Lane B expects. Idempotent; CI and local both run it.
#
# Order matters (found in the POC): changing the keyguard or navigation mode *after* animations
# are disabled can leave the status-bar inset animation stuck half-way on API 36, and every
# screenshot on that boot then shows a clipped status bar.
set -euo pipefail
a() { adb shell "$@" </dev/null; }

# 1. System UI changes, with animations still enabled so their transitions complete.
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  a settings put global "$s" 1
done
a svc power stayon true
a settings put system screen_off_timeout 2147483647
a input keyevent KEYCODE_WAKEUP
a wm dismiss-keyguard
a cmd uimode night no
a cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton >/dev/null 2>&1 \
  || echo "warning: could not pin 3-button navigation"
a settings put system font_scale 1.0
a settings put secure show_ime_with_hard_keyboard 0
a settings put secure spell_checker_enabled 0
a settings put global hide_error_dialogs 1
a settings put global sysui_demo_allowed 1
a input keyevent KEYCODE_HOME
# On cold boot System UI can size the status bar before the display cutout is known
# (63 px bar under a 128 px cutout on API 36, found in the POC). A night-mode round trip
# makes it re-lay out with the cutout applied.
bottom() { a dumpsys window | grep -m1 -oE "type=$1 frame=\[0,0\]\[[0-9]+,[0-9]+\]" | grep -oE '[0-9]+\]$' | tr -d ']'; }
for attempt in 1 2 3; do
  a cmd uimode night yes; sleep 2
  a cmd uimode night no; sleep 3
  status_bar=$(bottom statusBars); cutout=$(bottom displayCutout)
  [[ -z "$cutout" || -z "$status_bar" || "$status_bar" -ge "$cutout" ]] && break
  echo "status bar $status_bar px < cutout $cutout px; re-laying out System UI (attempt $attempt)"
done

# 2. Freeze window and transition animations once System UI has settled. animator_duration_scale
#    stays at 1: at 0 the system-bar show animation froze half-way on some API 36 boots (3 of 5
#    in the POC). App animations are frozen by DeviceShotRule's paused Compose clock instead, and
#    its settle loop waits out the short platform animations.
a settings put global window_animation_scale 0
a settings put global transition_animation_scale 0
a settings put global animator_duration_scale 1

# 3. Gate: status bar at least as tall as the display cutout.
status_bar=$(bottom statusBars); cutout=$(bottom displayCutout)
if [[ -n "$cutout" && -n "$status_bar" && "$status_bar" -lt "$cutout" ]]; then
  echo "::error::status bar ($status_bar px) shorter than display cutout ($cutout px)"; exit 75
fi

# 4. Gate: no ANR dialog. (Half-shown system bars are caught per capture by DeviceShotRule,
#    which compares visible insets with their full size; dumpsys leash names are not reliable.)
if a dumpsys window | grep -qiE "Application Not Responding|isn't responding"; then
  echo "::error::ANR dialog on the emulator"; exit 75
fi
echo "Emulator ready (status bar ${status_bar:-?} px): $(a getprop ro.build.fingerprint | tr -d '\r')"
