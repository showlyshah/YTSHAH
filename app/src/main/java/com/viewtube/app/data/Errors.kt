package com.viewtube.app.data

import java.io.IOException

/** Turns an exception into something a normal person can read. */
fun errorText(e: Throwable): String = when {
    e is IOException -> "Can't connect. Check your internet connection and try again."
    !e.message.isNullOrBlank() -> e.message!!.lineSequence().first().take(200)
    else -> "Something went wrong. Please try again."
}
