# How testing works

## The four kinds of tests

**Unit tests** (`./run test`) run on the JVM in seconds:

- `ChatViewModelTest` covers sending, rich replies, buttons, quick replies, errors and retry.
- `ReplyParserTest` covers every reply block type, unknown types and the legacy format.
- `MockBackendTest` covers the mock's scenarios and its JSON handling.

**Preview screenshots** (`./run previews`) run on the JVM in seconds:

- Every `@Preview` in `feature/chat/src/debug/ChatPreviews.kt` is rendered by Roborazzi
  (on Robolectric) and compared with its golden in `feature/chat/src/test/screenshots/`.
  Roborazzi generates the tests; there is no test code to write.
- Previews render on a Pixel 6 (411×914 dp, `PHONE` in `preview-kit/`), the same profile as
  the emulator screenshots. `@ThemeVariants` renders a preview in light, dark and font scale
  1.5. Multiple screen sizes aren't a requirement for this app, so the skill's 9-size matrix
  and its 400×500 dp "mobile" size are intentionally left out.
- On a mismatch, comparison images are in `feature/chat/build/outputs/roborazzi/`.
  To accept an intended change, run `./run previews-record` and review the image diff.

**Behavior tests** (`./run ui-test`) run on the emulator:

- `ChatInteractionTest` taps the card buttons and quick-reply chips against a fake backend.
- `HttpChatServiceContractTest` calls the running mock backend with the app's real HTTP
  client. It's skipped if `./run backend` isn't running.

**Screenshot tests** (`./run shots`) run on the emulator:

- `ChatDeviceShots` takes the loaded conversation in light and dark, and the error state.
- Each capture is compared with a golden image in
  `feature/chat/src/androidTest/screenshots/`.
- On a mismatch, reference, actual and diff images are written to
  `feature/chat/build/test-results/dropshots/`.

## Reports

Each screenshot run writes a self-contained HTML report to `build/reports/screenshots/` and
opens it (not on CI, where it's uploaded as an artifact; set `SHOTS_NO_OPEN=1` to skip opening).

| Run | Report shows |
|---|---|
| `./run previews`, `./run shots` | Failed first (golden, actual, diff), then passed. Goldens with no capture are flagged "not captured" |
| `./run previews-record`, `./run shots-record` | New, changed (before and after), removed and unchanged goldens, compared with the previous set |

For emulator screenshots, `DeviceShotRule` saves every capture under `actual/` because Dropshots
only writes images for failures.

## Preview or emulator screenshot?

Default to a **preview**: add a `@Preview` to `ChatPreviews.kt` and run `./run previews-record`.

Use an **emulator screenshot** only when the screen depends on system UI: drawing behind the
status bar, clearing the navigation bar, system-bar icon contrast, notifications,
picture-in-picture. Previews can't show those because there are no system bars on the JVM.
Every emulator screenshot says why in `@DeviceScreenshot(reason = "...")`.

## Adding an emulator screenshot test

1. If the module doesn't have it yet, add `id("shots.device")` to its `plugins {}`.
2. Run `./run shots-new MyScreen`. It creates the test and a fixtures file.
3. Fill in the `reason` and the fixture, then run `./run shots`. The first run reports
   "reference missing", which is expected.
4. In the real setup, add the `record-shots` label to the PR and CI records the goldens.
   For this POC: `SHOTS_LOCAL_RECORD=1 ./run shots-record`.

## Why the emulator needs special handling

Screenshots compare pixels, so anything that changes between runs causes a false failure.
`./run shots-flake` cold-boots the emulator several times and compares on each boot. These
are the problems it found, all now handled:

| What went wrong | Why | Where it's fixed |
|---|---|---|
| Status bar icons changed between runs | Demo mode didn't freeze the mobile signal icon | `DeviceShotRule` hides the cellular icons |
| Whole screen shifted 65 px on some boots | The status bar was sized before the screen cutout was known | `configure-device.sh` re-lays out System UI and fails the boot if it's still wrong |
| Both navigation styles enabled | The overlay command doesn't switch the other one off | `configure-device.sh` uses `enable-exclusive` |
| Recording saved no goldens | Dropshots 0.6.0 behaves this way with `recordOnFailure = false` | Default kept; `./run shots-record` restores goldens if a test fails |
| `/error` didn't trigger an error | Android escapes `/` in JSON and the mock didn't decode it | Mock JSON parsing (caught by the contract test) |
| Preview tests crashed with `NoSuchMethodError` | Roborazzi 1.78.0 is built against ComposablePreviewScanner 0.9.1, not the newer 0.10.0 | Pinned 0.9.1 in `gradle/libs.versions.toml` |
| Google's AGP 9.5 preview screenshot suite did nothing | The experimental test-suite setup registered no tasks in this module | Replaced with Roborazzi; same `@Preview`s, so it can be revisited later |

## Open issues

- **About 1 cold boot in 6** shows the status-bar clock shifted left, a second System UI
  layout race. The next step is a boot-time check of how System UI renders, then
  measuring on CI's x86_64 image.
- **The light-mode status bar icons are dark on the dark-blue header.** The screenshots show it
  (a real bug, not fixed yet).
- **The CI workflow hasn't been run,** and `tooling/emulator/emulator.lock` still has
  placeholders.

## Where things live

| Path | What |
|---|---|
| `screenshot-kit/` | `DeviceShotRule`, `@DeviceScreenshot` (emulator screenshots) |
| `preview-kit/` | `@ThemeVariants` (preview screenshots) |
| `build-logic/` | the `shots.device` Gradle plugin |
| `tooling/emulator/` | AVD config, boot scripts, device setup, version lock |
| `tooling/screenshots/` | verify/record script, scaffold templates, flake check, `PngDiff` |
| `.github/workflows/device-shots.yml` | CI: unit tests, preview and emulator screenshots, record-on-label |

## Upgrading the CI emulator

1. Update `tooling/emulator/emulator.lock` (URLs, sha256, `EXPECTED_FINGERPRINT`);
   `tooling/emulator/print-lock.sh` prints current values.
2. Re-record the goldens: every screenshot changes in one commit.
3. Review a sample of the diffs, then run `./run shots-flake 10` before merging.
