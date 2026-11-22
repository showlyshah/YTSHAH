# Morphe MicroG RE integration repair (2026-11-23)

## Findings from the Morphe MicroG RE source

- Morphe MicroG RE is GmsCore, package `app.revanced.android.gms`; it is installed separately and is not a library to embed in ViewTube.
- Its Android account authenticator implements `getAuthToken` and routes the requested auth-token service through Morphe's authorization and consent flow.
- The previous ViewTube implementation only launched Android's account chooser and saved the selected email. It did not request an OAuth token, so calling that a completed sign-in was incorrect.
- The Morphe source's legacy `GoogleSignInClient.getSignInIntent()` currently throws `UnsupportedOperationException`; ViewTube must not rely on that unsupported method.

## Current ViewTube implementation

1. Detects the exact Morphe authenticator package registered for account type `com.google`.
2. Opens Android's account chooser, which delegates add/select-account UI to the installed Morphe authenticator.
3. After an account is selected, requests an OAuth token using Android `AccountManager.getAuthToken` with token type:
   `oauth2:https://www.googleapis.com/auth/youtube.readonly`.
4. Waits for Morphe's authenticator/consent flow to complete before marking the account linked.
5. Checks that a non-empty token was returned, then discards it. ViewTube does not persist, log, or display the raw token.
6. Disconnect removes only ViewTube's local linked-account marker. It does not remove the Android account or revoke the authorization grant held by Morphe.

## Important limitation

This is now an actual Morphe-backed YouTube read-only OAuth authorization request, not just an account chooser. However, it is **not** a YouTube website login and does not create YouTube web cookies. The app's NewPipe Extractor playback path uses a separate cookie-based session; this OAuth token is not automatically consumed by that extractor. Do not claim that this fixes YouTube playback authentication, IP blocks, bot checks, or anonymous playback restrictions.

If the goal is authenticated NewPipe playback, a separate supported cookie/session integration is required. Do not try to convert or expose the OAuth token as a browser cookie.

## Source backups

Full source files were backed up immediately before this repair:

- `MicroGAccountSignIn.kt.before-morphe-oauth-token-flow.backup`
- `AccountScreen.kt.before-morphe-oauth-token-flow.backup`

Earlier backups remain in this folder:

- `MicroGAccountSignIn.kt.before-morphe-microg-re.backup`
- `AccountScreen.kt.before-morphe-microg-re.backup`

## Restore

To roll back only this OAuth-flow repair, restore the two `before-morphe-oauth-token-flow` files to:

- `app/src/main/java/com/viewtube/app/data/MicroGAccountSignIn.kt`
- `app/src/main/java/com/viewtube/app/ui/AccountScreen.kt`

Do not restore unrelated feed/player files.
