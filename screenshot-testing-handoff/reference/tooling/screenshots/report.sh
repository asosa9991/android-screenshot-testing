#!/usr/bin/env bash
# Writes the HTML report for a screenshot run (see report.py) and opens it locally.
# Not opened on CI or when SHOTS_NO_OPEN=1.
set -uo pipefail
cd "$(dirname "$0")/../.."
path=$(python3 -I tooling/screenshots/report.py "$@") || { echo "(could not write the screenshot report)"; exit 0; }
echo "Report: $path"
if [[ "${CI:-}" != "true" && "${SHOTS_NO_OPEN:-}" != "1" ]]; then open "$path" 2>/dev/null || true; fi
