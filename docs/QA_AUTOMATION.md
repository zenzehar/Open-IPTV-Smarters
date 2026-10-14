# Koky TV automated quality environment

## Scope and truth-in-reporting

This suite targets the isolated `app.opentv.qa` package. It never installs over the
user's `app.opentv.debug` package, imports personal provider credentials, or
resets the user's device. A successful Gradle/CI job proves compilation and the
specific assertions that ran. **It does not prove all 88 requested functions.**

The source of truth for requirements is
[`tests/requirements-coverage.csv`](../tests/requirements-coverage.csv).
`scripts/report-coverage.py` produces a device-specific Markdown/JSON evidence
report. Requirements not directly asserted are `NOT_AUTOMATED`,
`UNIT_LOGIC_ONLY` or `OPEN_REAL_DEVICE_REGRESSION`, not PASS.

## Tests included

| Layer | Test | What it proves |
|---|---|---|
| Gradle JVM/Robolectric | `./gradlew test` | Parser, catalogue freshness, history reset, seek arithmetic, episode sequencing, profile rules, safe errors |
| Python provider contracts | `python3 scripts/test-mock-xtream-server.py` | Valid/invalid login, account response, categories, 2,305 movies, two seasons, M3U, XMLTV, 206/416 byte ranges |
| Android instrumentation | `MockProviderIntegrationTest` | Actual Android Room/source repository, valid/invalid Xtream auth, Live catalogue, full cached VOD pagination, scoped title search, series data, playable-range endpoint, MainActivity launch |
| Android Compose UI instrumentation | `VodUiContractTest` | Poster short click, Continue Watching short click, touchscreen long click, confirmation/reset action, title-page action |
| Media3 Android instrumentation | `Media3SyntheticSeekTest` | When synthetic MP4 exists: ExoPlayer starts, repeated seeks to several times preserve a ready player and requested timestamp. **Does not inspect decoded pixels.** |
| Phone/TV emulator smoke | `scripts/emulator-smoke.sh` | QA APK installation, cold launch, D-pad input without process death, force-stop/relaunch, test instrumentation, screenshot/UI hierarchy/crash log collection |
| Requirement evidence reporting | `scripts/report-coverage.py` | Honest per-requirement status, including what has not run and user-reported regressions still open |

## Fixtures and isolation

The mock panel is `scripts/mock-xtream-server.py`. It is Python standard library
only, bound to **127.0.0.1:18080** on the host. Android emulator routing uses
`http://10.0.2.2:18080` and dummy username `fixture` / password
`testonly`. These are deliberately synthetic credentials.

The fixture includes two categories per media kind, 12 Live entries, 2,305 movie
entries, nine series, two seasons, an XMLTV feed, M3U data, account expiry,
and deterministic HTTP status/range responses. The build installs `ffmpeg` when needed and **requires**
generation of an 18-second MP4 with known timestamps. Both the Android test
package and mock-provider emulator job receive this same media file. If it
cannot be generated or served, the relevant job **fails, never passes by skip**.

The Android emulator is erased only within its **QA test package**. Test runs
must never point the fixture tests at a non-emulator or personal production device.

## Reproduce locally

Requirements: Android SDK (API 35 build platform, emulator with API 30 or later
and matching architecture), JDK 17, Gradle wrapper, Python 3, ADB, plus optional
ffmpeg. A hardware accelerated emulator is strongly recommended.

```bash
python3 scripts/test-mock-xtream-server.py
./gradlew test assembleQa assembleQaAndroidTest
python3 scripts/mock-xtream-server.py --media-file /tmp/koky-fixture.mp4
```

Generate the required fixture before compiling instrumentation tests. CI does
this automatically; local builds can call the `make_video` function from
`scripts/mock-xtream-server.py` with a path under
`app/src/androidTest/assets/qa_seek_fixture.mp4`.

With the emulator booted, in another terminal:

```bash
adb install -r app/build/outputs/apk/qa/app-qa.apk
adb install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
adb shell am instrument -w -r app.opentv.qa.test/androidx.test.runner.AndroidJUnitRunner
```

The two APK output names vary by Android Gradle Plugin version; inspect each
folder and use the generated name. The CI workflow handles locating those APKs.

Emulator screenshots, hierarchy, Java and native crash logs, fixture logs,
Android test output, and per-requirement JSON/Markdown are uploaded with the
GitHub Actions jobs. CI jobs run automatically for this PR, are isolated per
emulator, and fail when an assertion fails or the test service cannot start.

## What remains impossible to certify using these emulators alone

* **Actual device hardware codecs and native VLC shutdown:** Android TV vendors
  ship different GPU/decoder/ABI stacks. An intermittent return to Android Home
  may be a native process crash; only the affected TV's logcat/tombstone and
  physical reproduction can establish the root cause.
* **Real provider authentication/TLS/stream availability:** Fixtures prove
  expected client semantics, not the behaviour of an external provider.
* **Pixel-perfect screenshots and D-pad focus order:** Capturing a screenshot
  and sending keys does not assert the selected widget. UI instrumentation
  presently checks selected Continue Watching interactions, not every screen.
* **SMB/NAS offline sync:** A full two-device live sync test requires an
  isolated network NAS test service and two emulator instances with distinct
  storage and scheduling; current tests cannot mark it PASS.
* **Android WorkManager exact timing:** Timed background jobs may be delayed
  by the operating system; unit tests cover due-time decisions, not wall-clock
  execution guarantees.
* **Stable in-place updates:** CI's ad hoc debug signing must not be treated as
  a permanent release certificate.
* **Whole-app feature coverage:** See 88-row matrix; missing behavioural tests
  are explicitly identified and must not be inferred from a passing build.

## Real-TV diagnostics

For the reported intermittent series/VLC crash, capture logs immediately
after reproducing on an authorized development device:

```bash
adb logcat -b crash -d > KokyTV-crash.txt
adb logcat -d -v threadtime > KokyTV-logcat.txt
```

Before sharing, redact credentials, tokens, stream URLs and device identifiers.
Do not enable arbitrary remote ADB access on public networks.

PR #6 must remain **open and unmerged** until explicitly approved.
