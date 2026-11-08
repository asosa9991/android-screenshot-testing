#!/usr/bin/env python3
"""Writes an HTML report for a screenshot verify or record run.

    report.py verify-previews <module-dir>
    report.py verify-shots    <module-dir>
    report.py record-previews <module-dir> <previous-goldens-dir>
    report.py record-shots    <module-dir> <previous-goldens-dir>

The report is self-contained (images are copied next to it), so it survives later runs and
can be uploaded as a CI artifact. Prints the path of the report's index.html.
"""
import filecmp
import glob
import html
import json
import os
import re
import shutil
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# status: (label, sort order, css class)
STATUS = {
    "failed": ("Failed", 0, "bad"),
    "not-captured": ("Not captured", 1, "bad"),
    "new": ("New", 2, "new"),
    "changed": ("Changed", 3, "new"),
    "removed": ("Removed", 4, "warn"),
    "passed": ("Passed", 5, "ok"),
    "unchanged": ("Unchanged", 5, "ok"),
}


def nice_name(path):
    name = os.path.splitext(os.path.basename(path))[0]
    name = re.sub(r"^com\.example\.[a-z]+\.[A-Za-z]+Kt\.", "", name)
    name = name.replace("_WITH_BACKGROUND", "").replace("_PIXEL_6", "")
    return name.replace("_", " · ")


class Report:
    def __init__(self, kind, title, subtitle, module_dir):
        self.kind = kind
        self.title = title
        self.subtitle = subtitle
        self.entries = []
        self.notes = []
        slug = module_dir.strip("/").replace("/", "-")
        self.dir = os.path.join(ROOT, "build", "reports", "screenshots", f"{slug}-{kind}")
        shutil.rmtree(self.dir, ignore_errors=True)
        os.makedirs(os.path.join(self.dir, "img"))
        self.copied = 0

    def image(self, path):
        """Copies an image next to the report and returns its relative URL."""
        self.copied += 1
        dest = os.path.join("img", f"{self.copied:03d}-{os.path.basename(path)}")
        shutil.copy(path, os.path.join(self.dir, dest))
        return dest

    def add(self, name, status, images, note=""):
        self.entries.append({
            "name": name,
            "status": status,
            "images": [(label, self.image(p)) for label, p in images if p and os.path.exists(p)],
            "note": note,
        })

    def write(self):
        self.entries.sort(key=lambda e: (STATUS[e["status"]][1], e["name"]))
        counts = {}
        for e in self.entries:
            counts[e["status"]] = counts.get(e["status"], 0) + 1
        bad = counts.get("failed", 0) + counts.get("not-captured", 0)
        review = sum(counts.get(s, 0) for s in ("changed", "new", "removed"))
        if bad:
            verdict = ("bad", f"{bad} need attention")
        elif self.kind.endswith("record"):
            verdict = ("new", f"Review {review} change{'s' if review != 1 else ''}") if review else ("ok", "No changes")
        else:
            verdict = ("new", f"{review} new") if review else ("ok", "All good")

        out = [PAGE_HEAD.replace("$TITLE", html.escape(self.title))]
        out.append(f"<h1>{html.escape(self.title)}</h1>")
        out.append(f"<p class=d>{html.escape(self.subtitle)} · {time.strftime('%Y-%m-%d %H:%M')}</p>")
        out.append(f"<p class=summary><span class='pill {verdict[0]}'>{verdict[1]}</span>")
        for status, n in sorted(counts.items(), key=lambda kv: STATUS[kv[0]][1]):
            label, _, css = STATUS[status]
            out.append(f"<span class='pill {css}'>{n} {label.lower()}</span>")
        out.append("</p>")
        for note in self.notes:
            out.append(f"<pre class=note>{html.escape(note)}</pre>")

        attention = [e for e in self.entries if STATUS[e["status"]][1] < 5]
        calm = [e for e in self.entries if STATUS[e["status"]][1] >= 5]
        if attention:
            out.append("<h2>Needs a look</h2>")
            out.extend(self.card(e, wide=True) for e in attention)
        if calm:
            label = STATUS[calm[0]["status"]][0]
            out.append(f"<details {'open' if not attention else ''}><summary><h2>{label} "
                       f"<span>{len(calm)}</span></h2></summary><div class=grid>")
            out.extend(self.card(e, wide=False) for e in calm)
            out.append("</div></details>")

        path = os.path.join(self.dir, "index.html")
        with open(path, "w") as f:
            f.write("\n".join(out))
        return path

    def card(self, e, wide):
        label, _, css = STATUS[e["status"]]
        imgs = "".join(
            f"<figure><a href='{src}' target=_blank><img src='{src}' loading=lazy></a>"
            f"<figcaption>{html.escape(cap)}</figcaption></figure>"
            for cap, src in e["images"]
        ) or "<p class=d>No image.</p>"
        note = f"<p class=note>{html.escape(e['note'])}</p>" if e["note"] else ""
        return (f"<section class='card {'wide' if wide else ''}'><header><span class='pill {css}'>{label}</span>"
                f"<b>{html.escape(e['name'])}</b></header>{note}<div class=imgs>{imgs}</div></section>")


def verify_previews(module_dir):
    r = Report("previews-verify", "Preview screenshots: verify",
               f"{module_dir} · rendered on the JVM by Roborazzi", module_dir)
    goldens = os.path.join(ROOT, module_dir, "src/test/screenshots")
    seen = set()
    for f in sorted(glob.glob(os.path.join(ROOT, module_dir, "build/test-results/roborazzi/debug/results/*.json"))):
        res = json.load(open(f))
        golden = res.get("golden_file_path", "")
        if golden in seen:
            continue
        seen.add(golden)
        kind = res.get("type")
        name = nice_name(golden)
        if kind == "unchanged":
            r.add(name, "passed", [("Golden", golden)])
        elif kind == "changed":
            r.add(name, "failed", [("Golden", golden), ("Actual", res.get("actual_file_path")),
                                   ("Comparison: golden | diff | actual", res.get("compare_file_path"))],
                  "Doesn't match the golden. If the change is intended: ./run previews-record")
        elif kind == "added":
            r.add(name, "new", [("Actual", res.get("actual_file_path") or golden)],
                  "No golden yet. Record it with ./run previews-record")
    for golden in sorted(glob.glob(os.path.join(goldens, "*.png"))):
        if golden not in seen:
            r.add(nice_name(golden), "not-captured", [("Golden", golden)],
                  "This golden has no matching preview in this run. Was the preview removed or renamed?")
    return r.write()


def verify_shots(module_dir):
    r = Report("shots-verify", "Emulator screenshots: verify",
               f"{module_dir} · captured on the emulator by Dropshots", module_dir)
    goldens = os.path.join(ROOT, module_dir, "src/androidTest/screenshots")
    out = os.path.join(ROOT, module_dir, "build/test-results/dropshots/DebugAndroidTest")
    # DeviceShotRule saves every capture under actual/; Dropshots adds diff/ for mismatches.
    captured = set()
    for actual in sorted(glob.glob(os.path.join(out, "actual", "**", "*.png"), recursive=True)):
        rel = os.path.relpath(actual, os.path.join(out, "actual"))
        captured.add(rel)
        golden = os.path.join(goldens, rel)
        diff = os.path.join(out, "diff", rel)
        name = rel[:-4]
        if os.path.exists(diff):
            r.add(name, "failed", [("Golden", golden), ("Actual", actual), ("Dropshots diff", diff)],
                  "Doesn't match the golden. If the change is intended, re-record (CI: 'record-shots' label).")
        elif not os.path.exists(golden):
            r.add(name, "new", [("Actual", actual)], "No golden yet.")
        else:
            r.add(name, "passed", [("Golden", golden), ("Actual", actual)])
    for golden in sorted(glob.glob(os.path.join(goldens, "**", "*.png"), recursive=True)):
        rel = os.path.relpath(golden, goldens)
        if rel not in captured:
            r.add(rel[:-4], "not-captured", [("Golden", golden)],
                  "No screenshot was taken for this golden: the test failed before capturing, or didn't run.")
    # Failures that aren't pixel mismatches (infrastructure, crashes) only show up in the test results.
    for xml in glob.glob(os.path.join(ROOT, module_dir, "build/outputs/androidTest-results/connected/debug/*.xml")):
        text = open(xml, errors="replace").read()
        for case, message in re.findall(r'<testcase name="([^"]+)"[^>]*>\s*<failure[^>]*>([^<]{0,400})', text):
            message = html.unescape(message).strip().splitlines()[0]
            if "failed to match reference image" not in message:
                r.notes.append(f"{case}: {message}")
    return r.write()


def record(kind, module_dir, before_dir, goldens_rel, title):
    r = Report(f"{kind}-record", title, f"{module_dir} · new goldens compared with the previous ones", module_dir)
    after_dir = os.path.join(ROOT, module_dir, goldens_rel)

    def pngs(d):
        return {os.path.relpath(p, d) for p in glob.glob(os.path.join(d, "**", "*.png"), recursive=True)}

    before, after = pngs(before_dir), pngs(after_dir)
    for rel in sorted(before | after):
        b, a = os.path.join(before_dir, rel), os.path.join(after_dir, rel)
        name = nice_name(rel) if kind == "previews" else rel[:-4]
        if rel not in before:
            r.add(name, "new", [("New golden", a)])
        elif rel not in after:
            r.add(name, "removed", [("Removed golden", b)], "This golden was deleted: its test or preview no longer exists.")
        elif not filecmp.cmp(b, a, shallow=False):
            r.add(name, "changed", [("Before", b), ("After", a)], "Check that this change is intended before committing.")
        else:
            r.add(name, "unchanged", [("Golden", a)])
    return r.write()


PAGE_HEAD = """<!doctype html><meta charset=utf-8><title>$TITLE</title>
<style>
 body{font:14px/1.5 -apple-system,system-ui,sans-serif;margin:24px;background:#f4f6f8;color:#16202a}
 h1{margin:0 0 2px} h2{margin:28px 0 10px;display:inline-block;font-size:18px} h2 span{font-weight:400;color:#68737d;font-size:14px}
 summary{cursor:pointer} summary h2{margin:28px 0 10px}
 .d{color:#4b5661;margin:0 0 10px}
 .summary{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}
 .pill{display:inline-block;font-size:12px;font-weight:600;padding:2px 10px;border-radius:999px;border:1px solid currentColor}
 .ok{color:#0d7650} .bad{color:#b3261e} .new{color:#0b5f8a} .warn{color:#8a5a00}
 .grid{display:flex;flex-wrap:wrap;gap:14px;align-items:flex-start}
 .card{background:#fff;border:1px solid #d9dfe5;border-radius:8px;padding:12px;margin:0 0 14px}
 .card header{display:flex;gap:10px;align-items:center;margin-bottom:8px;font-family:ui-monospace,Menlo,monospace;font-size:13px}
 .imgs{display:flex;gap:12px;flex-wrap:wrap;align-items:flex-start}
 figure{margin:0} img{display:block;max-height:440px;max-width:100%;border-radius:4px;border:1px solid #e3e8ec}
 .card:not(.wide) img{max-height:300px}
 figcaption{font-size:12px;color:#4b5661;margin-top:4px}
 .note{color:#4b5661;margin:0 0 8px;white-space:pre-wrap}
 pre.note{background:#fff3f2;border:1px solid #f1c4c0;color:#7a1c16;padding:8px 10px;border-radius:6px;font-size:12px}
 @media (prefers-color-scheme:dark){body{background:#101418;color:#e3e8ec}.card{background:#182026;border-color:#2a343c}
  .d,figcaption,.note,h2 span{color:#9aa6b0}img{border-color:#2a343c}.ok{color:#42cf97}.bad{color:#ff8a80}.new{color:#8fd3ff}.warn{color:#f0b048}
  pre.note{background:#2a1513;border-color:#5c2a26;color:#ffb4ab}}
</style>"""


if __name__ == "__main__":
    mode, module = sys.argv[1], sys.argv[2]
    if mode == "verify-previews":
        print(verify_previews(module))
    elif mode == "verify-shots":
        print(verify_shots(module))
    elif mode == "record-previews":
        print(record("previews", module, sys.argv[3], "src/test/screenshots", "Preview screenshots: record"))
    elif mode == "record-shots":
        print(record("shots", module, sys.argv[3], "src/androidTest/screenshots", "Emulator screenshots: record"))
    else:
        sys.exit(f"unknown mode: {mode}")
