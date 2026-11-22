# Morphe MicroG RE integration (2026-11-23)

## Purpose
Configure ViewTube's account screen to identify and guide users to MorpheApp/MicroG-RE (GmsCore Redesign), rather than the upstream microG installation guide.

## Implementation
- Morphe MicroG RE application package: `app.revanced.android.gms`.
- `MicroGAccountSignIn.isMicroGInstalled()` already explicitly accepts this package when it is registered as the Android authenticator for account type `com.google`.
- AccountScreen now labels the integration as Morphe MicroG RE and links to the Morphe MicroG RE releases page.
- The account chooser still uses Android's public AccountManager chooser. It delegates to the installed authenticator; ViewTube does not bundle GmsCore.

## Important limitation
This connects/selects the Android Google account only. It does not obtain a YouTube OAuth token, import YouTube cookies, or guarantee authenticated YouTube playback / bypass IP restrictions. Do not describe this as YouTube session authentication.

## Backup files
- `MicroGAccountSignIn.kt.before-morphe-microg-re.backup`
- `AccountScreen.kt.before-morphe-microg-re.backup`

Both full source files were copied here before changes.

## Restore
Restore the backed-up `AccountScreen.kt` to `app/src/main/java/com/viewtube/app/ui/AccountScreen.kt` if the Morphe-specific labels/link cause a problem. The account helper was not modified because its existing authenticator detection already supports `app.revanced.android.gms`; its backup is retained for completeness.
