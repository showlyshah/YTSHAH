# Shorts swipe-to-mini-player backup

Date: 2026-11-23
Branch: rollback/previous-working-feed

Backed up complete source files before implementing Shorts swipe-down minimization:
- MainActivity.kt.before-shorts-miniplayer.backup
- RestoredScreens.kt.before-shorts-miniplayer.backup
- ShortsPlayerController.kt.before-shorts-miniplayer.backup

Reason:
The Shorts feed uses an isolated three-player preloading controller, while the existing MiniPlayer uses PlayerHolder's regular video player. The original files are retained in full so the previous navigation, feed, and player behaviour can be restored independently.

Restore:
Copy the corresponding backup file back to its original app/src/main/java path. These backups predate the Shorts mini-player change and include the already-confirmed Shorts keep-screen-on fix.
