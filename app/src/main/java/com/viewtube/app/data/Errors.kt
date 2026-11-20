package com.viewtube.app.data

import java.net.ConnectException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns an exception into something a normal person can read without hiding server responses. */
fun errorText(e: Throwable): String = when (e) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Can't connect. Check your internet connection and try again."
    is IOException -> e.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.take(200)
        ?: "A network request failed. Please try again."
    else -> e.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.take(200)
        ?: "Something went wrong. Please try again."
}
