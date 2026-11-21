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
- **Home**: endless feed with category chips (Music, Gaming, Live, News...), mixed with your subscriptions and what you watch
- **Shorts**: swipe-up vertical feed. **Creators**: discover channels by topic and subscribe
- Search with **search history** and live suggestions (tap the arrow to copy a suggestion into the box)
- Player: **video quality** and **audio track (language)** pickers, playback speed, fullscreen
- **Mini player**: press the down arrow (or Back) to shrink the video; it keeps playing above the tabs.
  The notification has play/pause and a **Stop** button; the X on the mini player also stops it
- Picture-in-picture when you leave the app mid-video; background audio with lock-screen controls
- **You** tab laid out like YouTube's: profile, History, Watch later, Settings, Help & feedback
- Opens youtube.com / youtu.be links and the Share menu
- Skips in-video sponsor reads, self-promo and "subscribe" reminders (SponsorBlock)

## Signing in and personal info
Google does not allow third-party apps to sign in to YouTube, and an app that asks for a Google
password is unsafe, so ViewTube does not do that. Instead:
- **Local profile**: everything is stored on the phone only (You tab).
- **Import subscriptions** from Google Takeout (`subscriptions.csv`) or a NewPipe backup.
- **Delete all my data** (Settings) wipes everything on the device.

MicroG-style sign-in (as in Morphe/ReVanced) works by patching Google's official YouTube app,
which this project deliberately does not use, so it can't be added to this client.

## Keeping it working
YouTube changes its site often. If videos stop loading, bump the version in
`app/build.gradle.kts` (`com.github.TeamNewPipe:NewPipeExtractor:...`) to the newest release tag
and rebuild.

## Notes
- Not affiliated with YouTube or Google. For personal use; using it is subject to YouTube's terms.
- This source was written without being compiled in the authoring environment (no Android SDK or
  Maven access there). Expect to fix a few small compile errors on first sync.
