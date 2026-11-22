# Unused Code / Repair Code

Purpose: a versioned recovery archive for useful code that is disabled, replaced, refactored, or temporarily removed. This is a backup/reference area; archived files are not compiled into the Android app.

## Mandatory change-safety rules

1. **Never discard working code silently.** Before removing, replacing, or substantially rewriting a working function, class, composable, or feature, copy its complete source into this archive.
2. Store complete source, not just a snippet. Preserve imports and any required adjacent helpers when they are part of the implementation.
3. Give every archive entry a clear filename and a matching metadata note containing:
   - unique ID / tag
   - original source path
   - source branch and commit/build when known
   - date archived
   - reason for archiving
   - what the code does / dependencies
   - restoration notes and known limitations
4. Use descriptive folders by feature and version, e.g. `PLAYER-CONTROLS/2026-11-23-before-swap/`.
5. Do not overwrite an older archive. Create a new versioned copy for each distinct implementation.
6. When restoring code, copy it back into the active source path, adapt only what is necessary, and keep the archived original unchanged.
7. Do not archive secrets, signing keys, credentials, or generated build outputs.
8. Keep this folder out of Android source compilation; it is documentation/recovery material only.

## Archive index

| Entry | Tag | Original source | Reason |
|---|---|---|---|
| BASELINE-BUILD-342/WatchScreen.kt | PLAYER-CONTROLS / PRE-RECENT-PLAYER-CHANGES | app/src/main/java/com/viewtube/app/ui/WatchScreen.kt | Earlier player-control implementation retained for repair/restoration. |
| BASELINE-BUILD-342/HomeScreens.kt | HOME-FEED-AND-SEARCH / BUILD-342 | app/src/main/java/com/viewtube/app/ui/HomeScreens.kt | Preserve the working home/search and paginated suggestion implementation. |
| BASELINE-BUILD-342/PlaybackService.kt | MEDIA-NOTIFICATION / CURRENT-SNAPSHOT | app/src/main/java/com/viewtube/app/player/PlaybackService.kt | Preserve the Media3 notification/session implementation for repair. |

**Important:** Archive snapshots are reference copies. The active app continues to use files under `app/src/main/java/`.
