# ViewTube

ViewTube is an Android YouTube viewer built with Kotlin, Jetpack Compose, NewPipeExtractor and
AndroidX Media3. It extracts video metadata and stream URLs and plays them in its own player.

## Current app

- Five-tab navigation: Home, Shorts, Subscriptions, Creators and You.
- Search, channel pages, video details and comments.
- Playback with quality selection, audio-track selection and playback speed controls.
- Fullscreen playback, picture-in-picture and background playback through a media session.
- Mini-player, playback progress/history and Watch later.
- SponsorBlock segment skipping.
- Local app data; no Google/YouTube password sign-in.

## Android compatibility

- Minimum SDK: 29 (Android 10).
- Compile SDK: 35.
- Target SDK: 35.
- Java/Kotlin target: 17.

Compatibility with newer Android releases depends on platform behavior and should be verified on
real devices. Update the compile/target SDK deliberately when the Android toolchain supports it.

## Build locally

1. Install Android Studio and the Android SDK platform/build-tools for API 35.
2. Open this repository in Android Studio and allow Gradle sync.
3. Connect an Android device with USB debugging enabled, or start an emulator.
4. Run the `app` configuration.

The Gradle Wrapper is committed to this repository, so no system Gradle installation is required. To build from a terminal:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The installable debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Continuous integration

GitHub Actions runs on pushes to `main` and `rebuild/**`, pull requests targeting `main`, and manual dispatch.
The workflow runs unit tests, Android lint, and assembles both debug and release variants. On a
successful build it uploads the debug APK as the `viewtube-debug-apk` workflow artifact and
publishes build reports for troubleshooting.

The release variant is currently unsigned. The debug APK is intended for testing and can be
installed directly after allowing installation from the source used to download it.

## Maintenance

YouTube extraction can break when YouTube changes its web implementation. If streams stop loading,
check the NewPipeExtractor release notes and update the dependency in `app/build.gradle.kts`,
then run the CI build and test on a physical device.

## Notes

- This project is not affiliated with YouTube or Google.
- Use the app in accordance with applicable laws and the services' terms.
