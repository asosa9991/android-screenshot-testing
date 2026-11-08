# Set up screenshot testing: instructions for the coding agent

You are setting up screenshot testing in this Android app (a Compose virtual-assistant app).
It already has UI tests, mocks and fakes. Add two kinds of screenshot tests, HTML reports for
every run, and agent skills so anyone (person or agent) can add new tests later.

Everything you need is in this hand-off folder, `screenshot-testing-handoff/`:

| File | Use it for |
|---|---|
| `PROMPT.md` | These instructions. Follow the phases in order. |
| `PITFALLS.md` | Every problem a proof of concept hit, with the fix. Read it before Phase 2. |
| `reference/` | Working code from that proof of concept (a chat app). **Adapt it; don't rewrite it from scratch.** |
| `skills/` | Skill templates to install in Phase 6. |

Background on the approach: Google's `testing-setup` Android skill
(https://github.com/android/skills/blob/main/testing/testing-setup/SKILL.md).

## What "done" looks like

1. **Preview screenshots (JVM, no emulator).** Every Compose `@Preview` in a feature's `src/debug` is
   rendered by Roborazzi on Robolectric and compared with a golden image.
2. **Emulator screenshots (instrumented).** A few screens are captured on a pinned emulator with
   the real status and navigation bars, using Dropshots and `DeviceShotRule`, then compared with
   goldens. These are for what only a device shows: edge-to-edge drawing, system-bar contrast,
   notifications, picture-in-picture.
3. **HTML reports.** Every compare and every record writes a self-contained HTML report (failures
   first, golden | actual | diff; for records: new, changed, removed) and opens it locally.
   `./screenshots goldens` opens a gallery of all goldens.
4. **One command.** `./screenshots <command> <module>`, run with no arguments for help.
5. **Skills.** `.github/skills/add-preview-screenshot`, `add-device-screenshot`, and
   `update-screenshot-goldens`, filled in with this repo's real names and paths.
6. **CI.** Preview screenshots on every PR. Emulator screenshots on PRs that touch UI. Reports
   uploaded as artifacts. Goldens recorded on CI through a PR label.

## Ground rules

- **Respect the existing stack.** Keep the DI framework, test runner, mocking library, module
  layout and naming conventions. Reuse the existing mocks and fakes; don't build parallel ones.
- **Don't upgrade AGP, Kotlin, Compose or compileSdk** as part of this work. If a library
  needs a newer version, stop and report it as a decision for the team.
- **Don't change how the app looks or behaves.** The only allowed production change is
  extracting a stateless composable from a ViewModel-bound screen, with identical visuals.
  Ask before doing this for more than 3 screens.
- **Never make a failing screenshot pass by** recording over it, raising a threshold, adding a
  sleep, or retrying. Find the cause (see `PITFALLS.md`). A test that only passes on retry
  counts as failing.
- **Verify, don't assume.** Every phase ends with commands you actually ran and their real
  output. If something can't be run here (for example CI), say so plainly.
- **Work on a branch.** Make one commit per phase if the team allows agent commits; otherwise
  stop for review at the end of each phase.
- **Stop and ask** when: a version conflict needs an upgrade; the app has no stateless
  composables to render; the emulator can't boot here; or a phase gate fails twice for an
  unknown reason.

## Phase 1: Learn the repo (no code changes)

Find and write down, in `docs/screenshot-testing/setup-report.md`:

1. Versions: AGP, Gradle, Kotlin, Compose BOM, compileSdk, minSdk, targetSdk, JDK.
2. Module layout: which modules have Compose screens, and whether each is an application or a library.
3. DI: Hilt, Koin, or something else. The custom test runner (`testInstrumentationRunner`).
   How UI tests swap in mocks (for example `@TestInstallIn` or `@UninstallModules`).
4. Existing tests: UI test framework, Robolectric or not, Test Orchestrator, any screenshot tooling.
5. Existing `@Preview`s: where they live, and whether they are stateless (state in, events out)
   or call `hiltViewModel()` or `viewModel()`.
6. Theme: the app theme composable, any dynamic color (wallpaper colors break goldens), dark-mode support.
7. Edge-to-edge: does the app call `enableEdgeToEdge()` or draw behind the system bars? Which screens?
8. CI: provider, workflow files, runner OS, whether KVM is available, Git LFS.
9. This machine: OS and CPU architecture, Android SDK path, installed system images, emulator version.

Then choose a **pilot module** and **pilot screen**: a feature library module whose main
screen has a stateless composable and draws behind the system bars (the assistant
conversation screen is a good candidate).

**Gate:** the report exists and names the pilot. Summarize it in five lines, then continue
unless a Stop-and-ask condition applies.

## Phase 2: Preview screenshots (Roborazzi)

Adapt from `reference/preview-kit/`, `reference/examples/feature.build.gradle.kts`,
`reference/examples/ChatPreviews.kt` and `reference/examples/ChatFixtures.kt`.

1. **Versions.** Start from Roborazzi 1.78.0, ComposablePreviewScanner **0.9.1** and
   Robolectric 4.17. You may use newer Roborazzi, but the scanner version must equal the one in
   Roborazzi's own `gradle/libs.versions.toml` at that release tag (see `PITFALLS.md`).
   Add them to the version catalog.
2. **`preview-kit` module** (Android library, debug-only dependency). It has:
   - `const val PHONE = "id:pixel_6"`, the same device profile as the emulator.
   - `@ThemeVariants`: light, dark, and font scale 1.5 on `PHONE`.

   No size matrix: multiple screen sizes are not a requirement.
3. **Pilot module setup.**
   - Apply `io.github.takahirom.roborazzi`.
   - `roborazzi { outputDir = src/test/screenshots; generateComposePreviewRobolectricTests { enable, packages = [the module's package], includePrivatePreviews = true, robolectricConfig sdk } }`.
   - `testOptions.unitTests.isIncludeAndroidResources = true`.
   - Test dependencies as in the reference.
   - `debugImplementation` for `preview-kit`, `ui-tooling` and `ui-test-manifest`.
4. **Fixtures.** In the module's `src/debug`, add a `<Feature>Fixtures` object with named UI
   states (`loaded`, `empty`, `loading`, `error`, plus rich-content states such as cards with
   buttons). Build them from the existing mocks and fakes where possible. No network, real
   clock, or randomness.
5. **Previews.** In `src/debug/.../<Feature>Previews.kt`:
   - `@ThemeVariants` on the main state.
   - `@Preview(device = PHONE, showBackground = true)` on the others.
   - Each preview wraps the stateless screen in the app theme. If dynamic color exists, turn it off.
6. **Record and check.** Record, then **look at the images**: right content, theme and size, and nothing blank.
7. **Gate.** All of these must hold:
   - Verify passes twice in a row after a clean build.
   - **Change detection:** temporarily change one color in the pilot screen. Verify must fail
     and name the affected previews. Revert, and verify passes again.

## Phase 3: Emulator screenshots (Dropshots + DeviceShotRule)

Adapt from `reference/screenshot-kit/`, `reference/build-logic/`, `reference/tooling/emulator/`
and `reference/examples/ChatDeviceShots.kt`.

1. **`screenshot-kit` module.** It has:
   - `DeviceShotRule`: demo-mode status bar, night mode set before launch, system-bar
     readiness check, stable capture, saving actual captures, and the Dropshots folder fix.
   - `@DeviceScreenshot(reason)`.
   - `ShotHostActivity`, which calls `enableEdgeToEdge()`.

   Keep every comment that explains a pitfall. They are the documentation.
   - **Hilt:** if screens need injection, make the host `@AndroidEntryPoint` and pass
     `DeviceShotRule(outer = HiltAndroidRule(this))`. Prefer stateless content with fixtures,
     which needs no injection.
   - **Application modules:** the host activity must be in the app under test. Declare it in
     the app's `src/debug` manifest, or keep emulator screenshots in library modules (preferred).
   - **Edge-to-edge:** if the app calls `enableEdgeToEdge()` with custom styles, copy those
     styles into the host so screenshots match the real app.
2. **Convention plugin** (`reference/build-logic/`). It applies Dropshots, keeps
   `recordOnFailure` at its default, and adds `screenshot-kit` to `androidTest`. Use the repo's
   existing build-logic and plugin-id style if it has one.
3. **Emulator scripts** (`reference/tooling/emulator/`): `boot.sh` (CI, pinned download),
   `boot-local.sh` (Apple Silicon, headless and window modes), `configure-device.sh` (order
   matters; read its comments), `config.ini` (Pixel 6 profile, `hw.keyboard=yes`, SwiftShader)
   and `emulator.lock`. Use the API level that matches `targetSdk`. Local image: whatever arm64
   image is installed. Never use `-atd` images.
4. **Pilot test.** Use the scaffold (`./screenshots shots-new <module> <Screen>`, Phase 5), then
   fill in a real `reason`. Capture light and dark for the pilot screen, plus one more state if
   it shows system UI differently (for example an error banner near the navigation bar).
   Content is the stateless screen plus fixtures, wrapped in the app theme.
5. **Record locally (bootstrap only):** `SHOTS_LOCAL_RECORD=1 ./screenshots shots-record <module>`.
   Look at the images: demo-mode clock 12:00, full battery, no mobile signal, 3-button navigation.
   Any system-bar contrast problem you see is a real finding; report it and don't fix it here.
6. **Gate.** All of these must hold:
   - Verify passes.
   - Change detection works, as in Phase 2.
   - `./screenshots flake <module> 5` reports 0 failed runs. If a run fails, open the saved
     evidence (`build/flake/run-N/`, `PngDiff` output) and match it to `PITFALLS.md` before
     changing anything.
   - Existing UI tests still pass. Screenshot tests are excluded from them with
     `notAnnotation=<package>.DeviceScreenshot`.

## Phase 4: HTML reports

Install `reference/tooling/screenshots/` (`report.py`, `report.sh`, `gallery.sh`, `shots.sh`,
`scaffold.sh`, `flake-check.sh`, `PngDiff.java`, templates) and `reference/screenshots`.
Adjust package names (`com.example.testing.DeviceScreenshot`) and paths.

**Gate:** run each path and check the report's headline and status pills:

| Run | Expected report |
|---|---|
| `./screenshots previews <m>` | "All good", N passed |
| `previews` with a temporary color change | "N need attention", failed cards show golden, actual, comparison |
| `./screenshots previews-record <m>` with the change | "Review N changes", before and after |
| `previews-record` after reverting | Changes back to the original; then `previews` passes |
| `./screenshots shots <m>` | "All good", golden and actual for each |
| `shots` with the color change | Only the screens that show that color fail, with a Dropshots diff |
| `./screenshots goldens` | Gallery with both kinds, grouped by module |

Revert every temporary change, and confirm the goldens are byte-identical to before (`git status` clean).

## Phase 5: The command and CI

1. `./screenshots` at the repo root (from `reference/screenshots`). If the repo already has a
   task runner (Makefile, `scripts/`, a `./run`), add the same commands there instead, and
   update the skills to match.
2. CI: adapt `reference/ci/screenshots.yml` to this repo's CI:
   - **Fast job:** unit tests and `./screenshots previews` for each module with previews.
     Always upload `build/reports/screenshots/`.
   - **Emulator job:** only when UI paths change, through a `changes` job that always reports.
     One emulator per job, sharded, with one boot retry for exit code 75 only.
   - **`gate` job:** the required check, which treats a skipped emulator job as success.
   - **`record` job:** on the `record-shots` label. It uses a GitHub App token so the pushed
     commit triggers CI again.
   - **Git LFS:** for `**/src/test/screenshots/**/*.png` and `**/src/androidTest/screenshots/**/*.png`.

   You can't run CI from here. Validate the YAML, list what still has to be filled in
   (`emulator.lock` values come from `tooling/emulator/print-lock.sh` on the first CI run;
   App id and secret), and say so in the final report.

## Phase 6: Skills, docs and agent rules

1. Copy `skills/*` to `.github/skills/`, one folder per skill with its `SKILL.md`. Replace
   every `{{PLACEHOLDER}}` with this repo's real values: module paths, package names, theme
   composable, plugin id, fixture and preview file names, the command. No placeholder may remain:

   | Placeholder | Fill in with (POC value) |
   |---|---|
   | `{{COMMAND}}` | The screenshot command (`./screenshots`) |
   | `{{APP_THEME}}` | The app theme composable (`AppTheme`) |
   | `{{PREVIEW_KIT_PACKAGE}}` | Package of `PHONE` and `@ThemeVariants` (`com.example.previews`) |
   | `{{TESTING_PACKAGE}}` | Package of `DeviceShotRule` and `@DeviceScreenshot` (`com.example.testing`) |
   | `{{DEVICE_PLUGIN_ID}}` | Convention plugin id (`shots.device`) |
   | `{{FEATURE_MODULE_EXAMPLE}}` | The pilot module (`feature:chat`) |
   | `{{FEATURE_PACKAGE_EXAMPLE}}` | The pilot module's package (`com.example.chat`) |
   | `{{STATELESS_SCREEN_EXAMPLE}}` | A real stateless screen signature (`ChatScreen(state: ChatUiState, …)`) |
   | `{{FIXTURES_FILE_EXAMPLE}}` | The pilot fixtures path (`feature/chat/src/debug/.../ChatFixtures.kt`) |
   | `{{PREVIEWS_FILE_EXAMPLE}}` | The pilot previews path (`feature/chat/src/debug/.../ChatPreviews.kt`) |
2. Add a short "Screenshot tests" section to `AGENTS.md` (create it if missing) and to
   `.github/copilot-instructions.md` (create it if missing). It should say:
   - Use the skills.
   - Previews are the default; emulator screenshots need a reason.
   - Never record over a failure you don't understand.
3. Write `docs/screenshot-testing.md`: the two kinds and when to use each, commands, where
   goldens and reports live, CI and the record label, and pitfalls (link to a copy of
   `PITFALLS.md`).
4. **Gate: use the skills for real.** Following only the skill text, add:
   - one preview screenshot for a second screen;
   - one emulator screenshot for a different system-UI case (for example a notification or the
     IME, if the app has one; otherwise a second edge-to-edge screen).

   Fix the skill text wherever you needed knowledge it didn't contain.

## Phase 7: Final report

Append to `docs/screenshot-testing/setup-report.md` and reply with a summary:

- What was added (modules, files, commands, CI jobs, skills).
- Test counts and timings: previews, emulator screenshots, flake runs, existing UI tests.
- Real UI findings from the goldens (for example system-bar contrast or clipped text).
  Report them; don't fix them.
- Anything not verified (CI, the record label) and what the team must fill in (secrets, lock values).
- Open issues, with evidence.
