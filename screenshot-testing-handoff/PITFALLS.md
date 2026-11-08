# Pitfalls found in the POC

Every row below cost real time in the proof of concept. The reference code in `reference/`
already handles all of them. If you write something new instead of adapting the reference,
check it against this list.

## Preview screenshots (Roborazzi, JVM)

| Symptom | Cause | Fix |
|---|---|---|
| Every generated preview test fails with `NoSuchMethodError: ComposablePreview.invoke(Composer, int)` | ComposablePreviewScanner version doesn't match the one Roborazzi was built against | Use the exact scanner version from Roborazzi's own `gradle/libs.versions.toml` at the same tag. Roborazzi 1.78.0 → scanner **0.9.1** (not 0.10.0) |
| Google's AGP 9.5 "screenshot test suite" (`testOptions.screenshotTests`) creates no Gradle tasks at all, with no error | Experimental feature; didn't register in a library module on AGP 9.5.1 | Use Roborazzi. The `@Preview`s stay the same, so the runner can be swapped later |
| Robolectric can't start the compose test activity | `ComponentActivity` isn't registered | `debugImplementation("androidx.compose.ui:ui-test-manifest")` |
| Resources missing or wrong in previews | Robolectric doesn't get merged resources | `testOptions.unitTests.isIncludeAndroidResources = true` |
| A preview renders blank, crashes, or differs each run | The preview calls `hiltViewModel()`, the network, the real clock, or `Random` | Previews render **stateless** composables with **fixture** state only |
| Previews look nothing like the device screenshots | Google's skill suggests a 400×500 dp "mobile" size, which is not a real phone | Render previews on the same device profile as the emulator: `device = "id:pixel_6"` (411×914 dp) |
| A preview isn't picked up | Its package isn't in `generateComposePreviewRobolectricTests.packages`, or it's private and `includePrivatePreviews` is off | Add the package; set `includePrivatePreviews = true` |
| Removed previews leave orphan goldens | Roborazzi record doesn't delete old files | The record command deletes goldens first and restores them if recording fails |

## Emulator screenshots (Dropshots + DeviceShotRule)

| Symptom | Cause | Fix |
|---|---|---|
| Recording "succeeds" but no goldens appear in `src/` | Dropshots 0.6.0 copies references only if the build **failed** or `recordOnFailure = true` | Keep the default (`true`). The record command restores the old goldens if any test fails, so a broken run never becomes the baseline |
| In an **app** module, record/pull finds nothing | Dropshots 0.6.0 plugin uses the test APK id for its device folder; the runtime uses the app id (dropbox/dropshots#115) | `DeviceShotRule` passes `rootScreenshotDirectory` for the test APK folder |
| Reports can't show passing captures | Dropshots writes images only on failure | `DeviceShotRule` saves every capture under `actual/`, which Dropshots' pull copies back |
| Record/pull doesn't run on Gradle Managed Devices | The Dropshots plugin hooks only `connected<Variant>AndroidTest` over adb | Use a scripted emulator (`tooling/emulator/`) for the screenshot job |
| Fails with more than one emulator attached | Dropshots' adb calls don't pass `-s <serial>` | One emulator per CI job; shard with `numShards` / `shardIndex` |
| ~30 px diff in the status bar between runs | SystemUI demo mode didn't freeze the mobile signal icon (API 36) | Demo mode hides mobile, no-SIM and airplane icons and pins Wi-Fi |
| Whole screen shifted ~65 px on some cold boots | SystemUI sized the status bar (63 px) before the display cutout (128 px) was known | Boot script does a night-mode round trip (up to 3 tries) and fails the boot as infrastructure (exit 75) if the bar is still shorter than the cutout |
| Light screenshots fail only after a dark one | Toggling night mode mid-run catches SystemUI re-laying out the bars | Change night mode only when it differs, then wait 2 s |
| `isVisible(WindowInsetsCompat.Type.systemBars())` is always false | `systemBars()` includes the caption bar, which phones never show | Check `statusBars() or navigationBars()` |
| A stale frame looks "stable" | The paused Compose test clock never recomposes state that arrives late (insets) | Advance the clock before every settle capture |
| Both gesture and 3-button navigation enabled | `cmd overlay enable` doesn't disable the other overlay | `cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton` |
| Device setup leaves SystemUI in odd states | Keyguard / navigation changes made **after** animations were disabled | Make SystemUI changes first with animations on; then set window and transition scales to 0. Keep `animator_duration_scale` at 1 |
| Blank or wrong screenshots on ATD images | ATD images disable hardware rendering; Google says screenshot tests aren't supported there | Use `default` (CI) or `google_apis` images, never `-atd` |
| Goldens differ between laptop and CI | arm64 (Apple Silicon) vs x86_64 render text slightly differently | Goldens are recorded on CI only; local emulator runs are advisory |
| ~1 cold boot in 6 shows the status-bar clock shifted left | A second SystemUI layout race (**open issue**, local arm64 Play image) | Treat as infrastructure; measure on the CI x86_64 `default` image. Idea: hash the demo-mode status bar at boot and retry the re-layout on mismatch |

## Build setup

| Symptom | Cause | Fix |
|---|---|---|
| "Dependency requires compileSdk 37" | 2026 AndroidX releases (Compose BOM 2026.09, lifecycle 2.11) need it | AGP 9 DSL: `compileSdk { version = release(37) { minorApiLevel = 1 } }` |
| Kotlin compose plugin version clash | AGP 9 bundles a Kotlin Gradle plugin | Set `org.jetbrains.kotlin.plugin.compose` to the Kotlin version AGP bundles (AGP 9.5.1 → 2.2.10) |

## Real bugs the screenshots caught

- Light-mode status bar icons were dark on a dark header (`enableEdgeToEdge()` assumes a
  light background in light mode). Only the emulator screenshots show this.
- With large text and a short screen, the last quick-reply chip was cut off.
- The backend contract test caught the mock not decoding `\/`, which Android's `org.json` writes.
