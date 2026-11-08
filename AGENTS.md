# Agent notes

Start with README.md. Every task goes through `./run` (no arguments prints the commands).

## Tests
- Logic → unit test in `feature/<name>/src/test`. Run `./run test`.
- Taps and interactions → Compose UI test in `src/androidTest`. Run `./run ui-test`.
- How a screen looks → add a `@Preview` (or `@ThemeVariants` for light/dark/large text) in
  `feature/<name>/src/debug`. It becomes a screenshot test automatically. Run `./run previews`.
- How a screen looks with the real status/navigation bars → emulator screenshot:
  `./run shots-new <Screen>`, use `DeviceShotRule`, fill in `@DeviceScreenshot(reason = "...")`.
- UI state comes from fixtures in `feature/<name>/src/debug`. Never the network or the real clock.
- Never record goldens locally in the real setup; CI records them via the `record-shots` PR label.

Details: docs/testing.md.
