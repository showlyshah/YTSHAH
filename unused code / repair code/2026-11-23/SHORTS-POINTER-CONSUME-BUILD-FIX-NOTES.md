# Shorts pointer-input build fix backup

Date: 2026-11-23
Original: app/src/main/java/com/viewtube/app/ui/RestoredScreens.kt
Backup: unused code / repair code/2026-11-23/RestoredScreens.kt.before-pointer-consume-build-fix.backup

The previous CI build reported an unresolved import for androidx.compose.ui.input.pointer.consume. The call uses PointerInputChange.consume() as an API member; the invalid extension import is removed. Gesture logic is otherwise unchanged.

Restore by copying the complete backup over the original source path.
