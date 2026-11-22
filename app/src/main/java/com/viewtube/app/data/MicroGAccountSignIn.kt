package com.viewtube.app.data

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent

/**
 * Connects a Google device account through Android's public account chooser.
 * The chooser delegates account selection/addition to the registered system
 * authenticator (microG when it is installed and registered for com.google).
 *
 * This links the device account only. It does not create YouTube web cookies
 * or claim that NewPipeExtractor playback is authenticated.
 */
object MicroGAccountSignIn {
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    private const val GOOGLE_SERVICES_PACKAGE = "com.google.android.gms"
    private const val PREFS = "viewtube_microg_account"
    private const val KEY_ACCOUNT_NAME = "linked_account"

    fun isMicroGInstalled(context: Context): Boolean {
        return try {
            val authenticator = AccountManager.get(context).authenticatorTypes
                .firstOrNull { it.type == GOOGLE_ACCOUNT_TYPE }
                ?: return false

            when (authenticator.packageName) {
                "com.mgoogle.android.gms", "app.revanced.android.gms" -> true
                GOOGLE_SERVICES_PACKAGE -> {
                    val info = context.packageManager.getApplicationInfo(authenticator.packageName, 0)
                    context.packageManager.getApplicationLabel(info)
                        .toString()
                        .contains("microg", ignoreCase = true)
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
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

    fun onAccountChosen(context: Context, data: Intent?): Result<String> {
        val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val accountType = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        if (accountType != GOOGLE_ACCOUNT_TYPE || accountName.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No Google account was selected. Sign-in may have been cancelled."))
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_NAME, accountName)
            .apply()
        return Result.success(accountName)
    }

    fun linkedAccount(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_NAME, null)

    fun disconnect(context: Context) {
        // This only disconnects ViewTube's local association. It never removes
        // the Google account from Android or from microG.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCOUNT_NAME)
            .apply()
    }
}
