# Archive metadata — BASELINE-BUILD-342

Archive date: 2026-11-23
Archive branch: `rebuild/open-source-feeds`
Purpose: initial recovery snapshot before further ViewTube repair work.

## 1. WatchScreen.kt
- Tag: `PLAYER-CONTROLS / PRE-RECENT-PLAYER-CHANGES`
- Original path: `app/src/main/java/com/viewtube/app/ui/WatchScreen.kt`
- Source commit: `59ee0ea33a5e92eef4f343f6ad6daf4fa25f6e05`
- Reason: preserve the earlier Media3 player-control implementation as a recovery reference while correcting the blue/yellow button action transfer.
- Restore: copy the full file back only after checking current changes; selectively restore the original control handler if only that behaviour is needed.

## 2. HomeScreens.kt
- Tag: `HOME-FEED-AND-SEARCH / BUILD-342`
- Original path: `app/src/main/java/com/viewtube/app/ui/HomeScreens.kt`
- Source commit: `59ee0ea33a5e92eef4f343f6ad6daf4fa25f6e05`
- Reason: preserve the home/search UI and paginated search suggestions implementation.
- Restore: compare with the active file before replacing; retain later unrelated fixes.

## 3. PlaybackService.kt
- Tag: `MEDIA-NOTIFICATION / CURRENT-SNAPSHOT`
- Original path: `app/src/main/java/com/viewtube/app/player/PlaybackService.kt`
- Source commit: `63da846388945d0ebc05fdf0526d6318d452c6b4`
- Reason: preserve the current Media3 media-session and notification controls implementation as a recovery reference.
- Restore: compare with the active file and device/CI behaviour before restoring.

## Integrity rule
Do not edit these archived source copies in place. Add a new dated/versioned archive entry for each later snapshot.
