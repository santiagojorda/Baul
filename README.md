# Baul

[![Tests](https://github.com/santiagojorda/Baul/actions/workflows/coverage.yml/badge.svg)](https://github.com/santiagojorda/Baul/actions/workflows/coverage.yml)
[![codecov](https://codecov.io/github/santiagojorda/Baul/graph/badge.svg?token=ANMJ48YDG6)](https://codecov.io/github/santiagojorda/Baul)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-30-blue)](app/build.gradle.kts)

Baul is a native Android app that watches folders in your gallery and automatically uploads new photos and videos to the destination you configure — YouTube, Google Photos, or Google Drive — then deletes the local original once the upload is confirmed. No Tasker, no third-party automation, no manual exports.

Instead of one fixed integration, Baul is built around configurable **rules**: each rule maps a folder to a destination with its own metadata (privacy, album, target folder, tags) and its own Google account, so you can run several rules at once — for example, a phone camera folder syncing to a private Google Photos album while a separate screen-recordings folder uploads unlisted to a specific YouTube channel.

## Features

- **Rule-based auto-sync** — pick a folder via the system picker (SAF) and route it to YouTube, Google Photos, or Drive, each with destination-specific metadata.
- **Multi-account support** — connect more than one Google account and assign a different account per rule.
- **Real-time detection** — a `ContentObserver` on `MediaStore` picks up new files as soon as they land in a watched folder, no polling.
- **Resilient background uploads** — `WorkManager` handles uploads with retry/backoff, survives process death, and respects a Wi-Fi-only setting per rule.
- **Safe delete** — the original file is only removed (via `MediaStore.createDeleteRequest`) after the destination API confirms the upload succeeded. With the optional "All files access" permission granted, it deletes directly instead of prompting the system confirmation dialog every time.
- **YouTube quota awareness** — tracks daily quota usage and queues remaining uploads for the next day instead of failing silently.
- **Upload history** — a log of every processed file with its status (uploaded, error, pending/retrying) grouped by folder, with manual retry/cancel per file. A separate **Logs** view lists every file with a recorded error across all folders, so a transient failure doesn't stay hidden while it retries on its own.
- **Home screen widget** — glanceable sync status without opening the app.
- **Clip editor** — trim and concatenate clips from a folder into a single highlight video (via Media3 Transformer), which can then feed into a sync rule like any other output folder.

## Tech stack

- Kotlin + Jetpack Compose, MVVM with domain/data separation
- Room for rules and upload history persistence
- WorkManager for background uploads
- Google Sign-In / Credential Manager for OAuth, multi-account
- Official Google client libraries for Drive and the YouTube Data API v3 (resumable uploads)
- Photos Library API (`photoslibrary.appendonly` scope) for Google Photos
- Media3 Transformer for clip trimming/concatenation
- Kover for coverage, reported to Codecov via GitHub Actions

## Getting started

```bash
git clone https://github.com/santiagojorda/Baul.git
cd Baul
./gradlew assembleDebug
```

Requires JDK 17. Open the project in Android Studio or run tests from the CLI:

```bash
./gradlew testDebugUnitTest
```

## Development shortcuts

A `Makefile` wraps the common Gradle/adb commands for testing and installing on a USB-connected device:

```bash
make test        # run unit tests (testDebugUnitTest)
make devices     # list phones connected over USB (adb devices -l)
make install     # build and install the debug build on the connected phone
make uninstall   # uninstall the app from the phone (asks for confirmation — wipes rules/history/Google accounts)
make reinstall   # uninstall + install (use this if adb reports INSTALL_FAILED_UPDATE_INCOMPATIBLE)
make apk         # build the debug APK (app/build/outputs/apk/debug/app-debug.apk)
make apk-release # build the release APK — unsigned, no signingConfig configured yet
```

`JAVA_HOME` and `ANDROID_HOME` default to `~/.jdks/jdk-17.0.19+10` and `~/Android/Sdk`; override either with `make install JAVA_HOME=/path/to/jdk-17` if yours live elsewhere.

## License

No license specified yet.
