# Ask Support: screenshot testing POC

A small Android chat app, built to prove out **reliable screenshot tests on an emulator**.

- You type a question, the app calls a backend, and the answer can be plain text or **rich
  content**: cards with buttons, numbered steps, quick-reply chips.
- The backend is a **mock** that runs on your laptop.
- The interesting part is the **test setup**: screenshots taken on an emulator that come out
  pixel-identical run after run.

The proposal this POC implements: [docs/pitch.html](docs/pitch.html).

## Everything goes through `./run`

```
./run              show all commands
```

| I want to… | Run |
|---|---|
| Use the app | `./run backend` in one terminal, then `./run app` in another |
| Run the fast tests (no emulator) | `./run test` and `./run previews` |
| Run the behavior tests on the emulator | `./run ui-test` |
| Run the emulator screenshot tests | `./run shots` |
| Add a screenshot of a screen state | Write a `@Preview` in `feature/chat/src/debug/`, then `./run previews-record` |
| Add an emulator screenshot (system bars) | `./run shots-new ProfileScreen` |
| Stop everything | `./run stop` |

The first emulator command boots an emulator (about 30 to 60 seconds). Later commands reuse it.

Every screenshot command (`previews`, `previews-record`, `shots`, `shots-record`) writes an
HTML report and opens it: failures first with golden, actual and diff side by side; for
records, what changed versus the previous goldens. Reports live in `build/reports/screenshots/`.
`./run goldens` opens a gallery of all current goldens.

## What's in the folders

```
app/              The Android app. Just wiring: starts the chat screen.
feature/chat/     The chat feature: screen, logic, and all of its tests.
mock-backend/     A fake server the app talks to during development.
screenshot-kit/   A small library that makes emulator screenshots reliable.
preview-kit/      PHONE (Pixel 6) and @ThemeVariants: one preview in light, dark and large text.
tooling/          Scripts behind ./run: emulator setup and screenshot helpers.
build-logic/      A Gradle plugin that adds the screenshot kit to a module.
docs/             The pitch, and how testing works.
```

Most work happens in **`feature/chat/`**:

```
feature/chat/src/
  main/             the code: ChatScreen, ChatViewModel, ChatService (calls the backend)
  debug/            ChatFixtures (sample conversations) and ChatPreviews (every @Preview is a screenshot test)
  test/             unit tests (JVM)
    screenshots/    golden images for the previews
  androidTest/      emulator tests: screenshots, button behavior, backend contract
    screenshots/    golden images for the emulator screenshots
```

## Four kinds of tests, and why

| Kind | Checks | Runs on | Command |
|---|---|---|---|
| Unit | Logic: sending, retry, parsing replies, the mock's answers | JVM, seconds | `./run test` |
| Preview screenshots | Every screen state looks right, in light, dark and with large text | JVM, seconds | `./run previews` |
| Behavior | Tapping a card button or chip does the right thing; the app and backend agree on the JSON | Emulator | `./run ui-test` |
| Emulator screenshots | Drawing behind the real status and navigation bars, in light and dark | Emulator | `./run shots` |

Most visual checks are preview screenshots: fast, no emulator, and they are just the
`@Preview`s you'd write anyway. Emulator screenshots are kept for what only a real device
shows: the system bars.

More detail, including what the POC found and the open issues: [docs/testing.md](docs/testing.md).

## Requirements

- macOS on Apple Silicon (Linux CI is set up in `.github/workflows/`, untested).
- Android SDK at `~/Library/Android/sdk` with the API 36 Google Play arm64 system image.
- JDK 17. The scripts find it automatically.
