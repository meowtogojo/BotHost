# BotHost — Android Studio starter project

This is an **early source-project scaffold**, not a finished/installable APK.
It establishes the Android app shell, a user-started foreground service, and
the Python runtime integration. The Node.js Mobile native runtime still needs
its official Android binary package and JNI bridge wired in before JavaScript
bots can run.

## What is included

- Kotlin Android application shell (`com.bothost.app`)
- Foreground service with an ongoing notification
- Chaquopy Python 3.13 runtime configuration
- `discord.py` and `python-dotenv` packaged as Python dependencies
- A Python script runner starter
- CMake/native bridge placeholder for Node.js Mobile

## Important limitations

1. This project has not been built or tested as an APK in this environment.
2. Node.js execution is **not enabled yet**. Node.js Mobile requires its native
   Android library (`libnode.so`), headers, NDK/CMake setup and a JNI entry point.
3. Node.js Mobile documents one Node runtime per app process and says restarting
   that runtime after it exits is unsupported. A robust multi-bot design needs
   a dedicated process supervisor, not simply one Node engine per bot.
4. Android may stop background work due to battery controls, memory pressure,
   force-stop, reboot, or manufacturer policies. A foreground service improves
   persistence but cannot guarantee 24/7 uptime.
5. Python packages with native extensions may not be compatible with Android.
   `discord.py` is primarily Python and is a suitable first test target.

## Build prerequisites

- Android Studio
- JDK 17
- Android SDK Platform 36
- Android NDK r24+ and CMake (required for Node.js Mobile)
- Internet access for Gradle dependency resolution

Open this folder in Android Studio and allow Gradle sync. The first build will
download Gradle and Chaquopy dependencies. The resulting APK will still be a
development shell until the Node.js Mobile binary and bridge are added.

## Python runtime

The Gradle config uses Chaquopy 17.2.0 and Python 3.13. Chaquopy requires
Android API 24+ and supports ARM64 devices. Official setup documentation:
https://chaquo.com/chaquopy/doc/current/android.html

## Node.js runtime integration milestone

Use the official Node.js Mobile Android guide and its v18.20.4 core library:
- Guide: https://nodejs-mobile.github.io/docs/guide/guide-android/getting-started/
- Releases: https://github.com/nodejs-mobile/nodejs-mobile/releases

The official guide requires the `include/node` headers and ABI-specific
`libnode.so` files, then links them through CMake and starts Node via JNI.
Place the release's Android files under `app/libnode/` so the expected paths are:

- `app/libnode/include/node/node.h`
- `app/libnode/bin/arm64-v8a/libnode.so`
- `app/libnode/bin/x86_64/libnode.so`

Then implement the `node::Start(argc, argv)` bridge in `native-lib.cpp`, and
expose it through a Kotlin/Java manager. The exact release archive layout
should be checked before copying files.

## Next implementation milestones

1. Finish Node.js Mobile native bridge and validate a tiny `node -e` script.
2. Add private app storage for imported `.py`, `.js`, and project folders.
3. Add encrypted-at-rest token storage; never print tokens in logs.
4. Add per-bot lifecycle state, logs, stop/restart controls and crash reporting.
5. Add dependency installation/import workflow with clear compatibility errors.
6. Add boot handling only with explicit opt-in and Android's required user-visible
   background-start restrictions.
7. Test on the target phone and build a signed release APK.

## Security

Bot tokens are credentials. Store them using Android Keystore-backed encryption,
redact them from logs, and never upload them to a third-party service. Only import
and run bot code you trust.


## Phone-only build path

A GitHub Actions workflow is included at `.github/workflows/android-build.yml`.
You can upload this project to a GitHub repository from your phone, then use
the repository's Actions tab to build the debug APK on a hosted runner. This
uses cloud compute only for compiling; bots are still intended to run locally
on the Android device. The first build is a starter APK, not a finished
multi-bot host, and JavaScript execution remains unimplemented.
