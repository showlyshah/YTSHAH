# Player button swap — pre-change recovery snapshot

Date: 2026-11-23
Branch: `rebuild/open-source-feeds`
Source snapshot commit: `914ccd3e00236c7692d16e9923c13cec2e3556a2`

## WatchScreen.kt
- Tag: `PLAYER-YELLOW-NEXT-CONTROL / BEFORE-DIRECT-HANDLER-SWAP`
- Original path: `app/src/main/java/com/viewtube/app/ui/WatchScreen.kt`
- Reason: preserve the full current WatchScreen before removing the ForwardingPlayer workaround and restoring the direct Media3 next-button click handler.
- Restore note: full file snapshot; compare before restoring to avoid losing later fixes.

## RestoredScreens.kt
- Tag: `MINI-PLAYER-BLUE-NEXT-BUTTON / BEFORE-REMOVAL`
- Original path: `app/src/main/java/com/viewtube/app/ui/RestoredScreens.kt`
- Reason: preserve the full MiniPlayer implementation, including the blue SkipNext button handler, before moving that action to the yellow in-player Next control.
- Restore note: full file snapshot; use the archived handler as the source of truth if the moved action needs to be recovered.

These archived files are reference-only and are not compiled into the app.
