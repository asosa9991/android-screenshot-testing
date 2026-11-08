#!/usr/bin/env bash
# Builds build/goldens.html: every golden image in the project, grouped, with labels.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=build/goldens.html; mkdir -p build

section() { # title, description, find-root
  local files; files=$(find "$3" -name '*.png' 2>/dev/null | sort)
  [[ -z "$files" ]] && return
  echo "<h2>$1 <span>$(echo "$files" | wc -l | tr -d ' ') images</span></h2><p class=d>$2</p><div class=grid>"
  while IFS= read -r f; do
    label=$(basename "$f" .png | sed -E 's/^com\.example\.[a-z]+\.[A-Za-z]+Kt\.//; s/_WITH_BACKGROUND//; s/_/ · /g')
    echo "<figure><a href=\"../$f\" target=_blank><img src=\"../$f\" loading=lazy></a><figcaption>$label</figcaption></figure>"
  done <<< "$files"
  echo "</div>"
}

{
  cat <<'HTML'
<!doctype html><meta charset=utf-8><title>Screenshot goldens</title>
<style>
 body{font:14px/1.5 -apple-system,system-ui,sans-serif;margin:24px;background:#f4f6f8;color:#16202a}
 h1{margin:0 0 4px} h2{margin:32px 0 2px} h2 span{font-weight:400;color:#68737d;font-size:14px}
 .d{color:#4b5661;margin:0 0 12px;max-width:70ch}
 .grid{display:flex;flex-wrap:wrap;gap:16px;align-items:flex-start}
 figure{margin:0;background:#fff;border:1px solid #d9dfe5;border-radius:8px;padding:8px}
 img{display:block;max-height:420px;max-width:100%;border-radius:4px}
 figcaption{font:12px ui-monospace,Menlo,monospace;color:#4b5661;margin-top:6px;max-width:300px;overflow-wrap:anywhere}
 @media (prefers-color-scheme:dark){body{background:#101418;color:#e3e8ec}figure{background:#182026;border-color:#2a343c}.d,figcaption,h2 span{color:#9aa6b0}}
</style>
<h1>Screenshot goldens</h1><p class=d>The reference images tests compare against. Click an image for full size.</p>
HTML
  # Every module that has goldens, in both lanes.
  for d in $(find . -path '*/src/androidTest/screenshots' -type d -not -path '*/build/*' | sort); do
    section "Emulator screenshots: ${d#./}" "Captured on the emulator with real system bars (./screenshots shots)." "$d"
  done
  for d in $(find . -path '*/src/test/screenshots' -type d -not -path '*/build/*' | sort); do
    section "Preview screenshots: ${d#./}" "Every @Preview, rendered on the JVM by Roborazzi (./screenshots previews)." "$d"
  done
} > "$out"
echo "$out"
