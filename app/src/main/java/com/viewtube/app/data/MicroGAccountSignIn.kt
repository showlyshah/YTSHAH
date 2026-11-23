package com.viewtube.app.data

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Morphe MicroG RE integration.
 *
 * The Android account chooser only selects/adds a device account. A real
 * authorization step must then request an OAuth token from the installed
 * account authenticator. ViewTube deliberately does not persist or log the
 * returned token. NewPipe's cookie-based extractor session is separate.
 */
object MicroGAccountSignIn {
    private fun trace(event: String, outcome: String = "info", detail: String? = null, error: Throwable? = null) {
        RuntimeDiagnostics.record("MICROG_ACCOUNT", event, outcome, detail, error)
    }

    const val GOOGLE_ACCOUNT_TYPE = "com.google"
    const val MORPHE_PACKAGE = "app.revanced.android.gms"
    const val YOUTUBE_READ_ONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val YOUTUBE_TOKEN_TYPE = "oauth2:$YOUTUBE_READ_ONLY_SCOPE"
    private const val PREFS = "viewtube_microg_account"
    private const val KEY_ACCOUNT_NAME = "linked_account"

    fun authenticatorPackage(context: Context): String? {
        return runCatching {
            AccountManager.get(context).authenticatorTypes
                .firstOrNull { it.type == GOOGLE_ACCOUNT_TYPE }
                ?.packageName
        }.getOrNull()
    }

    /**
     * Do not require AccountManager to expose an authenticator entry to detect
     * the installed app. MicroG RE may be installed and available to GMS clients
     * even when the authenticator list is empty or temporarily unavailable.
     */
    fun isMorpheMicroGInstalled(context: Context): Boolean {
        val packageInstalled = runCatching {
            context.packageManager.getPackageInfo(MORPHE_PACKAGE, 0)
            true
        }.getOrDefault(false)

        val authenticator = authenticatorPackage(context)
        val detected = packageInstalled || authenticator == MORPHE_PACKAGE
        trace("detect-provider", if (detected) "found" else "missing", "packageInstalled=" + packageInstalled + "; authenticator=" + (authenticator ?: "none"))
        return detected
    }

    fun detectionDetails(context: Context): String {
        val installed = runCatching {
            context.packageManager.getPackageInfo(MORPHE_PACKAGE, 0)
            true
        }.getOrDefault(false)
        val authenticator = authenticatorPackage(context) ?: "not reported"
        return "Morphe package: " + (if (installed) "installed" else "not found") +
            "; Google authenticator: " + authenticator
    }

    fun createAccountChooserIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            "Choose a Google account for ViewTube",
            null,
            null,
            null
        )

    fun selectedAccountName(data: Intent?): Result<String> {
        val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val accountType = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        trace("picker-result-parse", if (accountType == GOOGLE_ACCOUNT_TYPE && !accountName.isNullOrBlank()) "selected" else "invalid", "dataPresent=" + (data != null) + "; accountType=" + (accountType ?: "missing") + "; accountNamePresent=" + (!accountName.isNullOrBlank()))
        if (accountType != GOOGLE_ACCOUNT_TYPE || accountName.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException("Morphe MicroG RE did not return a selected Google account.")
            )
        }
        return Result.success(accountName)
    }

    /**
     * Requests a YouTube Data API read-only OAuth token through Android's
     * AccountManager. Morphe MicroG RE handles consent and token retrieval.
     * The raw token is checked for success and immediately discarded.
     */
    suspend fun authorizeYoutubeReadOnly(
        activity: Activity,
        accountName: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val account = Account(accountName, GOOGLE_ACCOUNT_TYPE)
        trace("oauth-request-start", "started", "scope=youtube.readonly; accountNameRedacted=true")
        try {
            AccountManager.get(activity).getAuthToken(
                account,
                YOUTUBE_TOKEN_TYPE,
                Bundle(),
                activity,
                { future ->
                    val result = runCatching {
                        val bundle = future.result
                        val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
                        val accountReturned = bundle.getString(AccountManager.KEY_ACCOUNT_NAME)
                        trace("oauth-callback", if (!token.isNullOrBlank()) "token-returned" else "empty-token", "tokenPresent=" + (!token.isNullOrBlank()) + "; accountReturned=" + (!accountReturned.isNullOrBlank()) + "; bundleKeys=" + bundle.keySet().sorted().joinToString(","))
                        if (token.isNullOrBlank()) {
                            val message = bundle.getString(AccountManager.KEY_ERROR_MESSAGE)
                                ?: "Morphe MicroG RE did not return an OAuth token."
                            throw IllegalStateException(message)
                        }
                        // Do not persist, log, or expose the OAuth token.
                        Unit
                    }
                    if (result.isSuccess) trace("oauth-request-finished", "success") else trace("oauth-request-finished", "failure", error = result.exceptionOrNull())
                    if (continuation.isActive) continuation.resume(result)
                },
                Handler(Looper.getMainLooper())
            )
        } catch (error: Exception) {
            trace("oauth-request-finished", "exception", error = error)
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

    fun saveLinkedAccount(context: Context, accountName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_NAME, accountName)
            .commit()
        val persisted = linkedAccount(context) == accountName
        trace("local-link-save", if (persisted) "verified" else "not-persisted", "accountNameRedacted=true")
    }

    fun linkedAccount(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_NAME, null)

    fun disconnect(context: Context) {
        trace("local-link-disconnect", "requested")
        // This removes only ViewTube's local link. It does not remove the
        // Android account or revoke grants held by Morphe MicroG RE.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCOUNT_NAME)
            .commit()
        trace("local-link-disconnect", if (linkedAccount(context) == null) "verified" else "not-cleared")
    }
}
