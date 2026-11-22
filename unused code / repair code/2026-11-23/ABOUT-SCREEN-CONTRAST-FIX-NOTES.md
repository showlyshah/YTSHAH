# About screen visibility fix backup

Date: 2026-11-23
Branch: rollback/previous-working-feed
Original: app/src/main/java/com/viewtube/app/ui/AccountScreen.kt
Backup: unused code / repair code/2026-11-23/AccountScreen.kt.before-about-screen-contrast-fix.backup

Reason:
The About screen was rendered as a child of the vertically scrolling Account screen and had no opaque background, allowing the underlying settings UI to show through. Its text relied on inherited content colors, reducing readability.

Change:
- Render About as a full-window Compose Dialog with platform width disabled.
- Give the dialog an opaque theme background and Surface.
- Apply explicit onSurface colors to body copy and headings.
- Preserve the existing About content and navigation.

Restore:
Copy this complete backup over app/src/main/java/com/viewtube/app/ui/AccountScreen.kt.
