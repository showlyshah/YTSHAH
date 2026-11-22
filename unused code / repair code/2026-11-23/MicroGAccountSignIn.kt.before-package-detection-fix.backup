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

    fun isMorpheMicroGInstalled(context: Context): Boolean =
        authenticatorPackage(context) == MORPHE_PACKAGE

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
                        if (token.isNullOrBlank()) {
                            val message = bundle.getString(AccountManager.KEY_ERROR_MESSAGE)
                                ?: "Morphe MicroG RE did not return an OAuth token."
                            throw IllegalStateException(message)
                        }
                        // Do not persist, log, or expose the OAuth token.
                        Unit
                    }
                    if (continuation.isActive) continuation.resume(result)
                },
                Handler(Looper.getMainLooper())
            )
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

    fun saveLinkedAccount(context: Context, accountName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_NAME, accountName)
            .apply()
    }

    fun linkedAccount(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_NAME, null)

    fun disconnect(context: Context) {
        // This removes only ViewTube's local link. It does not remove the
        // Android account or revoke grants held by Morphe MicroG RE.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCOUNT_NAME)
            .apply()
    }
}
