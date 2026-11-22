package com.viewtube.app.data

import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Starts Google account creation through Android's account authenticator.
 * When microG Services Core is installed as the com.google authenticator,
 * its own consent and sign-in UI handles the credentials.
 *
 * This links the device account only. It does not manufacture YouTube web
 * cookies or claim that NewPipeExtractor playback is authenticated.
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
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
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

    fun connect(context: Context, onComplete: (Result<String>) -> Unit) {
        val activity = context.findActivity()
        if (activity == null) {
            onComplete(Result.failure(IllegalStateException("Couldn't open the Android account sign-in screen.")))
            return
        }

        try {
            AccountManager.get(activity).addAccount(
                GOOGLE_ACCOUNT_TYPE,
                null,
                null,
                null,
                activity,
                AccountManagerCallback<Bundle> { future ->
                    val result = try {
                        future.result
                    } catch (error: Throwable) {
                        onComplete(Result.failure(error))
                        return@AccountManagerCallback
                    }

                    val accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME)
                    val accountType = result.getString(AccountManager.KEY_ACCOUNT_TYPE)
                    if (accountType == GOOGLE_ACCOUNT_TYPE && !accountName.isNullOrBlank()) {
                        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_ACCOUNT_NAME, accountName)
                            .apply()
                        onComplete(Result.success(accountName))
                    } else {
                        onComplete(Result.failure(
                            IllegalStateException("microG did not return a Google account. Sign-in may have been cancelled or the account authenticator is unavailable.")
                        ))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (error: Throwable) {
            onComplete(Result.failure(error))
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}
