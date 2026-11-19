package com.viewtube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Optional account sync through a Piped server (an open-source YouTube front-end).
 *
 * This is NOT a Google sign-in. Google does not allow third-party apps to sign in to YouTube,
 * and an app asking for a Google password would be unsafe. A Piped account is separate: you pick
 * a username and password, and your subscriptions follow you between devices and apps.
 * Only the sign-in token is kept on the device, encrypted. The password is never stored.
 */
class PipedClient(
    private val http: OkHttpClient,
    private val secure: SecureStore,
    private val store: LocalStore,
) {
    private val json = "application/json".toMediaType()

    val isSignedIn: Boolean get() = secure.get(TOKEN) != null
    private fun api() = store.pipedServer.value.trimEnd('/')

    suspend fun signIn(user: String, pass: String) = authenticate("login", user, pass)
    suspend fun register(user: String, pass: String) = authenticate("register", user, pass)

    fun signOut() {
        secure.put(TOKEN, null)
        store.setPipedUser(null)
    }

    private suspend fun authenticate(path: String, user: String, pass: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", user).put("password", pass).toString().toRequestBody(json)
        val req = Request.Builder().url("${api()}/$path").post(body).build()
        http.newCall(req).execute().use { r ->
            val obj = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()
            val token = obj?.optString("token")?.ifBlank { null }
            if (!r.isSuccessful || token == null) {
                val why = obj?.optString("error")?.ifBlank { null } ?: obj?.optString("message")?.ifBlank { null }
                throw IOException(why ?: "Could not sign in (error ${r.code})")
            }
            secure.put(TOKEN, token)
            store.setPipedUser(user)
        }
    }

    /** Merges subscriptions both ways so this device and the account end up identical. */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val remote = remoteSubscriptions()
        val added = store.addSubs(remote)
        val remoteIds = remote.map { it.id }.toSet()
        store.subs.value.filter { it.id !in remoteIds }.forEach { runCatching { post("subscribe", it.id) } }
        added
    }

    suspend fun subscribe(channelId: String) = withContext(Dispatchers.IO) { post("subscribe", channelId) }
    suspend fun unsubscribe(channelId: String) = withContext(Dispatchers.IO) { post("unsubscribe", channelId) }

    private fun remoteSubscriptions(): List<ChannelItem> {
        val req = Request.Builder().url("${api()}/subscriptions").header("Authorization", token()).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Could not load subscriptions (error ${r.code})")
            val arr = JSONArray(r.body?.string().orEmpty())
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val id = o.optString("url").substringAfterLast('/')
                ChannelItem(id, channelUrl(id), o.optString("name"), o.optString("avatar").ifBlank { null })
            }
        }
    }

    private fun post(path: String, channelId: String) {
        val body = JSONObject().put("channelId", channelId).toString().toRequestBody(json)
        val req = Request.Builder().url("${api()}/$path").header("Authorization", token()).post(body).build()
        http.newCall(req).execute().use { if (!it.isSuccessful) throw IOException("Sync failed (error ${it.code})") }
    }

    private fun token() = secure.get(TOKEN) ?: throw IOException("Not signed in")

    private companion object {
        const val TOKEN = "piped_token"
    }
}
