---
name: add-device-screenshot
description: Add an instrumented (emulator) screenshot test that captures a screen with the real Android status and navigation bars, using Dropshots and DeviceShotRule. Use only when the result depends on system UI, such as edge-to-edge drawing, system-bar icon contrast, notifications, picture-in-picture, the keyboard, WebView or SurfaceView. For anything else, use add-preview-screenshot.
---

# Add an emulator screenshot

Emulator screenshots are slower and need an emulator, so they're kept for what a JVM preview
can't show: real system UI. Each test states why in `@DeviceScreenshot(reason = "…")`, and
reviewers check that reason.

## Is a device really needed?

Use this skill only if one of these is true:
- The screen draws behind the status or navigation bar, and you need to check the insets or the system-bar icon contrast.
- The UI involves a notification, picture-in-picture, the keyboard (IME), WebView or SurfaceView.

Otherwise, use `add-preview-screenshot`.

## Steps

1. **The module needs the plugin once:** `plugins { id("{{DEVICE_PLUGIN_ID}}") }`. It adds
   Dropshots and the screenshot kit to `androidTest`.
2. **Scaffold:** `{{COMMAND}} shots-new <module> <ScreenName>`. This creates
   `src/androidTest/.../<Screen>DeviceShots.kt` and, if missing, a fixtures stub in `src/debug`.
3. **Fill in the test:**

   ```kotlin
   @DeviceScreenshot(reason = "Header draws behind the status bar; checks icon contrast in light and dark")
   class OrdersDeviceShots {
       @get:Rule val shots = DeviceShotRule()   // Hilt: DeviceShotRule(outer = HiltAndroidRule(this)) + @HiltAndroidTest

       @Test fun loaded_light() = shots.capture("loaded_light", group = "orders") {
           {{APP_THEME}} { OrdersScreen(OrdersFixtures.loaded) }
       }

       @Test fun loaded_dark() = shots.capture("loaded_dark", group = "orders", night = true) {
           {{APP_THEME}} { OrdersScreen(OrdersFixtures.loaded) }
       }
   }
   ```

   Imports: `{{TESTING_PACKAGE}}.DeviceScreenshot`, `{{TESTING_PACKAGE}}.DeviceShotRule`.
   - The `reason` must name the system-UI dependency. "Looks right" isn't a reason.
   - Use **light and dark** whenever the screen draws behind the system bars; icon contrast differs between them.
   - Content is the stateless screen plus a fixture, the same fixtures the previews use. No network, real clock or randomness.
   - `name` must be unique within `group`. It becomes `src/androidTest/screenshots/<group>/<name>.png`.
   - Keep it small: 2 or 3 captures per screen. Every capture adds emulator time to every PR.
4. **Run:** `{{COMMAND}} shots <module>`. It boots the screenshot emulator if needed, and a report opens.
   - On the first run, new tests fail with "reference missing". That's expected.
   - Check the actual images in the report:
     - The status bar shows the demo-mode values: 12:00, full battery, Wi-Fi, no mobile signal.
     - 3-button navigation.
     - Your content drawn correctly behind or around the bars.
5. **Goldens are recorded on CI,** not on laptops (Apple Silicon renders slightly differently):
   push, open the PR, and add the **`record-shots`** label. CI records on the pinned emulator and
   pushes the PNGs to your branch.
   - **Review the record report** in the CI artifacts before merging.
   - Local-only bootstrap, when there's no CI yet: `SHOTS_LOCAL_RECORD=1 {{COMMAND}} shots-record <module>`.
6. **Commit** the test and fixtures; the goldens arrive from CI.

## Don't

- Don't add `Thread.sleep`, retries or a looser `ResultValidator` to make it pass.
  `DeviceShotRule` already waits for the bars and for a stable frame.
- Don't change night mode, demo mode or system settings yourself; use `capture(night = …)`.
- Don't capture a single view. Capture the full screen; the system bars are the point.
- Don't use emulator screenshots for plain state coverage (empty, error, long text); use previews.

## If something goes wrong

The report puts failures first. Infrastructure problems appear as a red note at the top, and
"not captured" means the test failed before taking its screenshot.

| Symptom | Meaning and fix |
|---|---|
| `INFRA: system bars never fully appeared (…)` | The emulator's SystemUI isn't ready; not your test. Rerun `{{COMMAND}} shots`; if it repeats, run `{{COMMAND}} flake <module> 3` and report the evidence |
| `INFRA: screen never settled` | Something keeps animating: an infinite animation, a loader or a blinking cursor. Give the fixture a static state |
| Diff only in the status-bar clock or icons | Known emulator boot race (see `docs/screenshot-testing.md`). Rerun; if it persists, report it. Don't record over it |
| The whole screen shifted by about half the status-bar height | Status bar sized before the display cutout. The boot script should catch this (exit 75); report it if it didn't |
| Light screenshots fail, dark pass | Night-mode state leaking between tests. Make sure only `capture(night = …)` changes it |
| "Environment drifted" on CI | The emulator image changed. The lock file and goldens must be updated together; ask the screenshot owner |
