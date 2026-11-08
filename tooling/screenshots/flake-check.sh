#!/usr/bin/env bash
# Week-1 spike tool: cold-boot the emulator N times, run verify each time, count mismatches.
# With 0 failures in n captures, the 95% upper bound on the false-failure rate is 3/n.
# Evidence for every failed run is kept in build/flake/run-<i>/.
set -euo pipefail
module=$1; runs=$2; dir=${module//://}
source tooling/env.sh
export SHOTS_NO_OPEN=1   # one report per boot would flood the browser
rm -rf build/flake; mkdir -p build/flake
fail=0; captures=0; failed_captures=0
for i in $(seq 1 "$runs"); do
  echo "=== Cold boot $i/$runs ==="
  adb emu kill >/dev/null 2>&1 || true
  sleep 5
  tooling/emulator/boot.sh > "build/flake/boot-$i.log" 2>&1
  if tooling/screenshots/shots.sh verify "$module" -q > "build/flake/verify-$i.log" 2>&1; then r=pass; else r=FAIL; fail=$((fail + 1)); fi
  results="$dir/build/outputs/androidTest-results/connected/debug"
  n=$( (cat "$results"/*.xml | grep -o '<testcase ' || true) | wc -l | tr -d ' ')
  f=$( (cat "$results"/*.xml | grep -o '<failure' || true) | wc -l | tr -d ' ')
  captures=$((captures + n)); failed_captures=$((failed_captures + f))
  echo "run $i: $r ($n captures, $f failed)"
  if [[ $r == FAIL ]]; then
    out="build/flake/run-$i"; mkdir -p "$out"
    cp -R "$dir/build/test-results/dropshots/." "$out/"
    (cat "$results"/*.xml | grep -oE '<failure[^>]*>[^<]{0,200}' || true) | sed 's/<failure>//' > "$out/failures.txt"
    for actual in "$out"/DebugAndroidTest/reference/*/*.png; do
      golden="$dir/src/androidTest/screenshots/${actual#"$out"/DebugAndroidTest/reference/}"
      [[ -f "$golden" ]] && "$JAVA_HOME/bin/java" tooling/screenshots/PngDiff.java "$golden" "$actual" | grep -v ': 0 px' | sed 's/^/    /' || true
    done
  fi
done
echo
echo "Runs: $runs, failed runs: $fail, captures: $captures, failed captures: $failed_captures"
awk -v n="$captures" -v f="$failed_captures" 'BEGIN { if (n > 0 && f == 0) printf "95%% upper bound on false-failure rate per capture: %.1f%%\n", 300 / n }'
[[ $fail -eq 0 ]]
