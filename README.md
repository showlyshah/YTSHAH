# ViewTube

An ad-free YouTube viewer for Android 10+ (minSdk 29), built like NewPipe: it reads video data
straight from YouTube with [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
and plays it in its own player, so YouTube's ads, promoted videos and sponsored cards never load.
The interface follows the YouTube app (red logo, video cards, bottom tabs, light/dark theme).

## Build it
1. Install **Android Studio** (Ladybug or newer).
2. **File > Open** this folder and let Gradle sync (needs internet the first time).
3. Plug in a phone (USB debugging on) or start an emulator, press **Run**.
   For a shareable file: **Build > Build APK(s)**.

Targets API 35. Newer Android versions (up to 17) run it fine; raise `targetSdk` in
`app/build.gradle.kts` once your Android Studio has the newer SDK.

## What it does
- Home (trending + your subscriptions), search with suggestions, channels, comments, up-next
- Quality picker (up to the best YouTube offers), fullscreen, background audio with lock-screen controls
- Picture-in-picture when you leave the app mid-video
- Subscriptions, history, watch later, resume where you left off
- Opens youtube.com / youtu.be links and the Share menu
- Skips in-video sponsor reads, self-promo and "subscribe" reminders (SponsorBlock)

## Signing in and personal info
Google does not allow third-party apps to sign in to YouTube, and an app that asks for a Google
password is unsafe, so ViewTube does not do that. Instead (Account screen, top-right):
- **Local profile**: everything is stored on the phone only.
- **Import subscriptions** from Google Takeout (`subscriptions.csv`) or a NewPipe backup.
- **Piped account (optional)**: sign in or create one to sync subscriptions across devices.
  The token is stored encrypted with the Android Keystore; the password is never saved.
- **Delete all my data** wipes everything on the device.

## Keeping it working
YouTube changes its site often. If videos stop loading, bump the version in
`app/build.gradle.kts` (`com.github.TeamNewPipe:NewPipeExtractor:...`) to the newest release tag
and rebuild. Piped servers also come and go: you can change the server on the Account screen.

## Notes
- Not affiliated with YouTube or Google. For personal use; using it is subject to YouTube's terms.
- This source was written without being compiled in the authoring environment (no Android SDK or
  Maven access there). Expect to fix a few small compile errors on first sync.
