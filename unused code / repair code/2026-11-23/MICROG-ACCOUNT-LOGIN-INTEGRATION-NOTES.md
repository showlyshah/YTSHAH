# microG account sign-in integration

Date: 2026-11-23
Branch: rollback/previous-working-feed

## Files added
- app/src/main/java/com/viewtube/app/data/MicroGAccountSignIn.kt
  - Detects the standard microG, Vanced microG, and ReVanced GmsCore authenticator package variants by the registered com.google account authenticator.
  - Starts Android AccountManager addAccount for account type com.google.
  - Delegates credential entry and consent to the installed system authenticator (microG when microG Services Core is installed and registered).
  - Stores only the returned account display name locally in ViewTube preferences.
  - Disconnect clears only ViewTube's local association; it does not remove the device Google account.

## Existing files changed
- app/src/main/AndroidManifest.xml
  - Added package visibility queries for standard microG, Vanced microG, and ReVanced GmsCore package variants.
- app/src/main/java/com/viewtube/app/ui/AccountScreen.kt
  - Added Google account (microG) section, sign-in status, and local disconnect action.
  - Existing Piped sign-in and other settings retained.

## Full pre-change backups
- AndroidManifest.xml.before-microg-account-login.backup
- AccountScreen.kt.before-microg-account-login.backup

## Scope and limitation
This is Android Google-account registration through the microG account authenticator. It does not retrieve or store Google passwords, OAuth tokens, YouTube cookies, or browser session data. It does not currently authenticate NewPipeExtractor requests or YouTube playback. A separate supported YouTube session/authentication integration would be required for that; do not treat this account-link status as proof of authenticated playback.

## Restore
Restore the two existing source files from the full backups above. Delete MicroGAccountSignIn.kt to remove the new helper, and remove this note. The backups are retained; do not overwrite them.
