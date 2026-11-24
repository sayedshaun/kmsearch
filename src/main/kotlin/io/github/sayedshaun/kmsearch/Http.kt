package io.github.sayedshaun.kmsearch

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The default identity sent to engines. Wikimedia asks every client to say who it is; set your app's own via each engine's `userAgent`. */
public const val DEFAULT_USER_AGENT: String = "kmsearch/0.1 (+https://github.com/sayedshaun/kmsearch)"

/**
 * Runs [request] and returns the body text. A 429 or 403 (and a 202, which DuckDuckGo uses for its bot check) is an [EngineException]
 * with `blocked = true`; any other non-2xx is a plain failure. Cancelling the coroutine cancels the call.
 */
internal suspend fun OkHttpClient.fetch(request: Request): String {
    val response = suspendCancellableCoroutine<Response> { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(EngineException("could not be reached", cause = e))
            }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }
    return response.use {
        when {
            it.code == 429 || it.code == 403 || it.code == 202 -> throw EngineException("blocked or rate limited (HTTP ${it.code})", blocked = true)
            !it.isSuccessful -> throw EngineException("answered HTTP ${it.code}")
            else -> try { it.body.string() } catch (e: IOException) { throw EngineException("could not be read", cause = e) }
        }
    }
}
