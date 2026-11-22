# Unused code / repair code archive

This folder preserves complete prior implementations before replacement or repair.

## Archive policy
- Never discard working code during a feature change.
- Each archived file keeps its original repository path, source commit, branch, reason for backup, and restore instructions.
- Restore by copying the complete archived source section back to its original path.
- Keep archives even after a successful build so later regressions can be bisected or selectively reverted.

## 2026-11-23 — Player controls / aspect ratio / zoom
| Archived source | Original path | Reason / tags |
|---|---|---|
| `app__src__main__java__com__viewtube__app__ui__WatchScreen.kt.backup` | `app/src/main/java/com/viewtube/app/ui/WatchScreen.kt` | #watch-screen #player-ui #aspect-ratio #pinch-zoom — preserve previous working full player UI before adding zoom and aspect modes |
| `app__src__main__java__com__viewtube__app__player__PlayerHolder.kt.backup` | `app/src/main/java/com/viewtube/app/player/PlayerHolder.kt` | #player-engine #next-video #queue — preserve previous next/autoplay implementation before fallback repair |
| `app__src__main__java__com__viewtube__app__player__PlaybackService.kt.backup` | `app/src/main/java/com/viewtube/app/player/PlaybackService.kt` | #media-session #notification — preserve previous notification/session layout before restoring transport controls |
| `app__src__main__java__com__viewtube__app__data__Graph.kt.backup` | `app/src/main/java/com/viewtube/app/data/Graph.kt` | #regionalization #newpipe #localization — preserve previous extractor country/localization configuration |

All four source backups were copied from commit `e84784141c9acf21978f3b914e6eab2e97657173`.
