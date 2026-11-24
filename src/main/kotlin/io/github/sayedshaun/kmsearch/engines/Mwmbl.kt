package io.github.sayedshaun.kmsearch.engines

import io.github.sayedshaun.kmsearch.DEFAULT_USER_AGENT
import io.github.sayedshaun.kmsearch.Engine
import io.github.sayedshaun.kmsearch.EngineException
import io.github.sayedshaun.kmsearch.EngineResult
import io.github.sayedshaun.kmsearch.SearchQuery
import io.github.sayedshaun.kmsearch.fetch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Mwmbl, a free, open-source, non-profit web search engine with a public JSON API and no key. Its index is small, so it often returns
 * only a few results, but they are real web pages and add variety next to DuckDuckGo.
 */
public class MwmblEngine(
    private val http: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String = "https://mwmbl.org",
) : Engine {
    override val id: String = "mwmbl"

    @Serializable private class Span(val value: String = "")
    @Serializable private class Hit(val url: String = "", val title: List<Span> = emptyList(), val extract: List<Span> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery): List<EngineResult> {
        val url = "${baseUrl.trimEnd('/')}/api/v1/search/".toHttpUrl().newBuilder().addQueryParameter("s", query.text).build()
        val body = http.fetch(Request.Builder().url(url).header("User-Agent", userAgent).build())
        val hits = try { json.decodeFromString<List<Hit>>(body) } catch (e: Exception) { throw EngineException("sent an unreadable answer", cause = e) }
        return hits.mapNotNull { h ->
            val title = h.title.joinToString("") { it.value }.trim()
            if (h.url.isBlank() || title.isEmpty()) null else EngineResult(title, h.url, h.extract.joinToString("") { it.value }.trim())
        }
    }
}
