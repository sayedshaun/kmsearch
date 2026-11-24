package io.github.sayedshaun.kmsearch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Searches several [engines] at once and merges what comes back. It is meant to run on the user's own device, so each user
 * asks the engines from their own address and no one shares a quota.
 *
 * - An engine that is slow ([engineTimeoutMillis]) or fails is left out; the others still answer ([SearchResponse.statuses] says which failed).
 * - A failed engine is suspended for [suspendMillis] (twice as long if it was blocked), so a blocked engine is not hammered.
 * - Answers are cached by query for the [cache]'s lifetime. Pass `cache = null` to turn that off.
 */
public class MetaSearch(
    private val engines: List<Engine>,
    private val engineTimeoutMillis: Long = 6_000,
    private val suspendMillis: Long = 60_000,
    private val cache: ResultCache? = ResultCache(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    init { require(engines.isNotEmpty()) { "MetaSearch needs at least one engine" } }

    private val suspendedUntil = ConcurrentHashMap<String, Long>()

    public suspend fun search(query: SearchQuery): SearchResponse {
        val q = query.copy(text = query.text.trim())
        if (q.text.length < 2) return SearchResponse(q, emptyList(), emptyList())
        cache?.get(q)?.let { return it.copy(fromCache = true) }

        val outcomes = coroutineScope { engines.map { e -> async { run(e, q) } }.awaitAll() }
        val statuses = outcomes.map { it.status }
        val response = SearchResponse(q, merge(outcomes.map { it.engine.id to it.results }, q.limit), statuses)
        if (response.results.isNotEmpty()) cache?.put(q, response)
        return response
    }

    private class Outcome(val engine: Engine, val results: List<EngineResult>, val status: EngineStatus)

    private suspend fun run(engine: Engine, q: SearchQuery): Outcome {
        val until = suspendedUntil[engine.id]
        if (until != null && until > now()) {
            return Outcome(engine, emptyList(), EngineStatus(engine.id, ok = false, error = "temporarily suspended", suspended = true))
        }
        val start = now()
        return try {
            val results = withTimeout(engineTimeoutMillis) { engine.search(q) }
            suspendedUntil.remove(engine.id)
            Outcome(engine, results, EngineStatus(engine.id, ok = true, resultCount = results.size, tookMillis = now() - start))
        } catch (e: TimeoutCancellationException) {
            fail(engine, start, "timed out", blocked = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EngineException) {
            fail(engine, start, e.message ?: "failed", e.blocked)
        } catch (e: Exception) {
            fail(engine, start, "could not be reached", blocked = false)
        }
    }

    private fun fail(engine: Engine, start: Long, reason: String, blocked: Boolean): Outcome {
        suspendedUntil[engine.id] = now() + if (blocked) suspendMillis * 2 else suspendMillis
        return Outcome(engine, emptyList(), EngineStatus(engine.id, ok = false, tookMillis = now() - start, error = reason))
    }

    public companion object {
        /** The engines that work without a key or a browser: Wikipedia (official API) and DuckDuckGo (HTML page, best effort). */
        public fun default(
            http: okhttp3.OkHttpClient,
            userAgent: String = DEFAULT_USER_AGENT,
            cache: ResultCache? = ResultCache(),
        ): MetaSearch = MetaSearch(
            listOf(io.github.sayedshaun.kmsearch.engines.WikipediaEngine(http, userAgent), io.github.sayedshaun.kmsearch.engines.DuckDuckGoEngine(http, userAgent)),
            cache = cache,
        )
    }
}
