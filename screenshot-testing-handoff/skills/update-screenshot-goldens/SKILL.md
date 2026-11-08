---
name: update-screenshot-goldens
description: Handle a failing screenshot check, either preview (Roborazzi) or emulator (Dropshots). Decide from the HTML report whether the UI change is intended, then re-record the goldens safely, or find the real cause. Use when `previews` or `shots` fails locally or on CI, after an intended UI change, or when a screenshot test seems flaky.
---

# Update screenshot goldens

A failing screenshot is one of three things. Work out which before you touch any golden:

| What the report shows | It is | Do |
|---|---|---|
| The diff is exactly the UI change you made on purpose | An **intended change** | Re-record (below) |
| The diff is in a screen or area you didn't mean to change | A **regression** | Fix the code; don't record |
| The diff is in the status bar, the whole screen is shifted, or it fails only sometimes | **Infrastructure / flakiness** | Investigate (below); never record |

## 1. Read the report

- Locally, the report opens automatically. Its path is `build/reports/screenshots/<module>-<kind>-verify/index.html`.
- On CI, download the `preview-report` or `shots-*` artifact.

Failures come first: **golden | actual | diff**. Look at where the highlighted pixels are and
whether they match your change.

## 2a. Intended change: re-record

**Preview screenshots:**
1. Run `{{COMMAND}} previews-record <module>`. The record report opens.
2. Check that every **Changed** entry is a screen you meant to change, and that before and after look right.
3. Nothing should be **Removed** unless you deleted or renamed a preview.
4. Run `{{COMMAND}} previews <module>`. It must pass.
5. Commit the PNGs in `src/test/screenshots/`.

**Emulator screenshots:**
1. Push your change and add the **`record-shots`** label to the PR. CI re-records on the pinned emulator and pushes the PNGs to your branch.
2. Open the record report from the CI artifacts and check it the same way.
3. Never record emulator goldens on a laptop for real use; they would differ from CI's.

In the PR description, list which goldens changed and why.

## 2b. Regression

Fix the code, not the golden. Run `{{COMMAND}} previews <module>` or `{{COMMAND}} shots <module>`
until it passes against the **existing** goldens.

## 2c. Infrastructure or flakiness (emulator screenshots)

1. Look at the report's red note at the top for `INFRA:` messages. Those come from the device, not your code.
2. Run `{{COMMAND}} flake <module> 5`. It cold-boots the emulator 5 times and compares each time.
   Evidence for each failed run is in `build/flake/run-N/`, including where the pixels differ.
3. Match the evidence to the pitfalls table in `docs/screenshot-testing.md`.
4. If it's new, report it with that evidence. Don't record, raise thresholds, add sleeps or add retries.

## Rules

- Never record to make a check pass when you don't know why it failed.
- A golden change is a UI change: it needs the same review as code.
- Preview goldens can be recorded locally. Emulator goldens are recorded by CI only.
