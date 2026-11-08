#!/usr/bin/env bash
# Scaffolds an emulator screenshot test: ./screenshots shots-new <module> <ScreenName>
set -euo pipefail
dir=$1; screen=$2
base=${screen%Screen}
manifest_pkg=$(grep -h 'namespace' "$dir/build.gradle.kts" | head -1 | sed -E 's/.*"(.+)".*/\1/')
[[ -n "$manifest_pkg" ]] || { echo "No namespace found in $dir/build.gradle.kts"; exit 1; }
pkg_path=${manifest_pkg//.//}
group=$(echo "$base" | sed -E 's/([a-z0-9])([A-Z])/\1_\2/g' | tr '[:upper:]' '[:lower:]')

test_file="$dir/src/androidTest/kotlin/$pkg_path/${base}DeviceShots.kt"
fixture_file="$dir/src/debug/kotlin/$pkg_path/${base}Fixtures.kt"
[[ -e "$test_file" ]] && { echo "Already exists: $test_file"; exit 1; }

mkdir -p "$(dirname "$test_file")"
sed -e "s/__PACKAGE__/$manifest_pkg/g" -e "s/__BASE__/$base/g" -e "s/__SCREEN__/$screen/g" -e "s/__GROUP__/$group/g" \
  tooling/screenshots/DeviceShots.kt.template > "$test_file"
echo "Created $test_file"

if [[ ! -e "$fixture_file" ]]; then
  mkdir -p "$(dirname "$fixture_file")"
  sed -e "s/__PACKAGE__/$manifest_pkg/g" -e "s/__BASE__/$base/g" tooling/screenshots/Fixtures.kt.template > "$fixture_file"
  echo "Created $fixture_file (fill in the 'loaded' state)"
fi

module=${dir//\//:}
cat <<MSG

Next:
  1. Replace the TODO reason with the system-UI dependency this screen has.
  2. ./screenshots shots $module   (first run reports "reference missing", as expected)
  3. Open a PR and add the 'record-shots' label. CI records the goldens on the pinned emulator.
MSG
