#!/usr/bin/env bash
# Screenshot test commands behind ./screenshots shots, shots-record and shots-new.
#   shots.sh verify <module> [gradle args]
#   shots.sh record <module> [gradle args]
#   shots.sh new    <module> <ScreenName>
set -euo pipefail
cd "$(dirname "$0")/../.."
source tooling/env.sh
source tooling/emulator/emulator.lock

cmd=$1; module=$2; dir=${module//://}; shift 2
[[ -d "$dir" ]] || { echo "Unknown module '$module'."; exit 2; }

args=(
  -Pandroid.testInstrumentationRunnerArguments.annotation=com.example.testing.DeviceScreenshot
  -Pandroid.testInstrumentationRunnerArguments.deviceShots.fingerprint="$EXPECTED_FINGERPRINT"
)
[[ "${CI:-}" == "true" ]] && args+=(-Pandroid.testInstrumentationRunnerArguments.deviceShots.ci=true)

case "$cmd" in
  new)
    exec tooling/screenshots/scaffold.sh "$dir" "${1:?ScreenName}" ;;
  verify)
    tooling/emulator/boot.sh --reuse
    rm -rf "$dir/build/test-results/dropshots"   # no stale diffs in the report
    if ./gradlew ":$module:connectedDebugAndroidTest" "${args[@]}" "$@"; then status=0; else status=1; fi
    tooling/screenshots/report.sh verify-shots "$dir"
    if [[ $status == 0 ]]; then
      echo "Screenshots match the goldens."
    else
      echo "Screenshot mismatch or test failure: see the report."
      exit 1
    fi ;;
  record)
    if [[ "${CI:-}" != "true" && "${SHOTS_LOCAL_RECORD:-}" != "1" ]]; then
      echo "Goldens are recorded on CI: add the 'record-shots' label to your PR."
      echo "(POC only, to record locally: SHOTS_LOCAL_RECORD=1 ./screenshots shots-record <module>)"
      exit 1
    fi
    tooling/emulator/boot.sh --reuse
    goldens="$dir/src/androidTest/screenshots"
    backup=$(mktemp -d)
    [[ -d "$goldens" ]] && cp -R "$goldens/." "$backup/"
    # Delete first so goldens for removed or renamed tests disappear.
    find "$goldens" -name '*.png' -delete 2>/dev/null || true
    if ! ./gradlew ":$module:recordDebugAndroidTestScreenshots" "${args[@]}" "$@"; then
      # A failing run must never become the new baseline.
      rm -rf "$goldens" && mkdir -p "$goldens" && cp -R "$backup/." "$goldens/"
      echo "Recording aborted: a test failed. Goldens are unchanged."
      exit 1
    fi
    echo "Recorded $(find "$goldens" -name '*.png' | wc -l | tr -d ' ') goldens in $goldens/"
    tooling/screenshots/report.sh record-shots "$dir" "$backup" ;;
  *) echo "unknown command: $cmd"; exit 2 ;;
esac
