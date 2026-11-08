# Screenshot testing hand-off

This folder sets up screenshot testing in your virtual assistant app with your coding agent
(Luna 6 in GitHub Copilot). It's based on the proof of concept in this repo, including
everything that went wrong there.

| File | What it is |
|---|---|
| `PROMPT.md` | Step-by-step instructions for the agent: 7 phases, each with a check it must pass |
| `PITFALLS.md` | Every problem the POC hit, and the fix. The agent reads it before writing code |
| `reference/` | Working code from the POC (screenshot kits, scripts, HTML reports, CI) for the agent to adapt |
| `skills/` | Three agent skills, installed into your repo in Phase 6 |

## What you'll have at the end

- **Preview screenshots.** Every `@Preview` in a feature's `src/debug` becomes a test, rendered
  on the JVM (no emulator) in light, dark and large text.
- **Emulator screenshots** for screens that depend on the real status and navigation bars.
- **An HTML report** after every check and every re-record, plus a gallery of all goldens.
- **One command:** `./screenshots <command> <module>`.
- **Three skills** your team and agents use to add tests:
  - `add-preview-screenshot`: a new screen or state, previews only.
  - `add-device-screenshot`: system-bar and edge-to-edge checks on the emulator.
  - `update-screenshot-goldens`: what to do when a screenshot check fails.
- **CI:** previews on every PR; emulator screenshots on UI changes; goldens recorded on CI by
  adding a `record-shots` label to the PR.

## How to run it

1. **Copy this folder** to the root of your app's repo, as `screenshot-testing-handoff/`, and
   create a branch.
2. **Check this machine has:**
   - The Android SDK.
   - An arm64 emulator system image for your `targetSdk` API level (any `google_apis` image; not `-atd`).
   - JDK 17.
   - Python 3.

   No emulator needs to be running.
3. **Open Copilot Chat** in agent mode, choose Luna 6 in max mode, and send:

   > Read `screenshot-testing-handoff/PROMPT.md` and follow it phase by phase. Read
   > `PITFALLS.md` before Phase 2. Adapt the code in `reference/` instead of writing it from
   > scratch. After each phase, show me the gate results (the commands you ran and their output)
   > before continuing.

4. **Review at each checkpoint.** Here's what to look for:

| Phase | Check |
|---|---|
| 1. Learn the repo | `docs/screenshot-testing/setup-report.md`: are the versions, DI, mocks and pilot screen right? |
| 2. Previews | The record report: real content, your theme, nothing blank. The change-detection test failed, then passed |
| 3. Emulator | The record report: status bar shows 12:00, 3-button navigation. 5 cold boots, 0 failures. Existing UI tests still pass |
| 4. Reports | Every report shows the expected headline (the agent tests all of them) |
| 5. CI | The workflow fits your CI. It lists what you need to provide: a GitHub App for the record bot, and the emulator lock values |
| 6. Skills | The agent added two more tests using only the skills, and fixed the skills where they fell short |
| 7. Report | Real UI problems found in the goldens; anything not verified |

Expect a few hours of agent time, mostly emulator boots in Phases 3 and 6.

## Using the skills afterwards

Copilot loads skills from `.github/skills/<name>/SKILL.md` and picks one based on its
description. Ask in plain words:

- "Add preview screenshots for the settings screen: loaded, empty and error."
- "Add an emulator screenshot for the conversation screen with the keyboard open."
- "The screenshot check failed on my PR. What changed?"

You can also name the skill: "Use the add-preview-screenshot skill to…".

If your Copilot setup doesn't pick up skills, copy each `SKILL.md` to
`.github/prompts/<skill-name>.prompt.md` and run it as `/<skill-name>` in chat.

## After setup

When Phase 6 is done, delete `screenshot-testing-handoff/`. The agent copies `PITFALLS.md`
into `docs/`; the skills, scripts and docs are all in the repo.
