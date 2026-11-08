---
name: add-preview-screenshot
description: Add a preview screenshot test for a Compose screen or component state, rendered on the JVM by Roborazzi with no emulator. Use for any visual check that doesn't need the real status or navigation bars, such as a new screen, a new UI state (loading, empty, error, a rich assistant reply), dark mode, or large text. For edge-to-edge or system-bar checks, use add-device-screenshot instead.
---

# Add a preview screenshot

In this repo every `@Preview` in a feature module's `src/debug` is a screenshot test.
Roborazzi renders it on the JVM and compares it with a golden PNG. You write a preview and a
fixture; there's no test class.

## First, pick the right kind

- **Preview screenshot (this skill):** how a screen or component looks in a given state.
  This is the default.
- **Emulator screenshot (`add-device-screenshot`):** only if the result depends on real system
  UI, such as drawing behind the status bar, clearing the navigation bar, system-bar icon
  contrast, notifications, picture-in-picture or the keyboard.

## Steps

1. **Find the stateless composable** for the screen: it takes UI state and callbacks, with no
   `hiltViewModel()` or `viewModel()` inside. Example: `{{STATELESS_SCREEN_EXAMPLE}}`.
   - If only a ViewModel-bound version exists, extract a stateless overload with identical
     visuals and have the existing one call it. Keep the change minimal.
2. **Add or reuse a fixture** in the module's fixtures file, `{{FIXTURES_FILE_EXAMPLE}}`
   (in `src/debug`). A fixture is a named UI state: `val loaded = …`, `val error = …`.
   - Build it from the existing mocks and fakes where possible.
   - Use fixed values only: no network, `System.currentTimeMillis()`, `Random`, `UUID.randomUUID()` or locale-dependent formatting.
   - Times and dates are fixed strings or come from a fixed clock.
3. **Add the preview** to the module's previews file, `{{PREVIEWS_FILE_EXAMPLE}}` (in
   `src/debug`, package `{{FEATURE_PACKAGE_EXAMPLE}}` or a subpackage):

   ```kotlin
   // Main state of a screen: light, dark and large text in one annotation.
   @ThemeVariants
   @Composable
   private fun OrdersLoaded() = {{APP_THEME}} { OrdersScreen(OrdersFixtures.loaded) }

   // Other states: one preview each, on the same phone as the emulator.
   @Preview(device = PHONE, showBackground = true)
   @Composable
   private fun OrdersEmpty() = {{APP_THEME}} { OrdersScreen(OrdersFixtures.empty) }
   ```

   Imports: `{{PREVIEW_KIT_PACKAGE}}.PHONE` and `{{PREVIEW_KIT_PACKAGE}}.ThemeVariants`.
   - Name previews `<Screen><State>`. The name becomes the golden's file name.
   - Use `@ThemeVariants` for a screen's main state, and a single `@Preview(device = PHONE)` for other states.
   - Don't use other sizes or devices. Screen sizes aren't a requirement here.
   - For animations (loading shimmer, typing dots), the fixture shows a static state. Don't preview a running animation.
4. **If the module has no previews yet,** set it up once, copying an existing module such as
   `{{FEATURE_MODULE_EXAMPLE}}`:
   - The Roborazzi plugin, plus the `roborazzi { … generateComposePreviewRobolectricTests { packages = [...] } }` block.
   - `unitTests.isIncludeAndroidResources = true`.
   - The test dependencies.
   - `debugImplementation` for `preview-kit`, `ui-tooling` and `ui-test-manifest`.
5. **Record:** `{{COMMAND}} previews-record <module>`. A report opens.
   - Your new previews show as **New**.
   - Check each image: correct content, theme and text, nothing cut off or blank.
   - **Any "Changed" or "Removed" entry you didn't intend means you affected another screen.** Stop and find out why.
6. **Verify:** `{{COMMAND}} previews <module>` must pass.
7. **Commit** the preview, the fixture and the new PNGs in `src/test/screenshots/` (Git LFS).
   In the PR description, list the new goldens.

## Don't

- Don't call `hiltViewModel()`, repositories or the network from a preview.
- Don't turn on dynamic color (wallpaper-based) in previews.
- Don't record to make a failing check pass. If `previews` fails, read its report first
  (see `update-screenshot-goldens`).

## If something goes wrong

| Symptom | Fix |
|---|---|
| The preview isn't in the report | Its package isn't in the module's `roborazzi.generateComposePreviewRobolectricTests.packages`, or it isn't in `src/debug`/`src/main` |
| `NoSuchMethodError … ComposablePreview.invoke` | The ComposablePreviewScanner version must match Roborazzi's (see `docs/screenshot-testing.md`) |
| The image is blank or shows an error | The composable needs injection or a real dependency; give it fixture state instead |
| The image changes between runs | Something non-deterministic: time, random IDs, async loading, an animation |
