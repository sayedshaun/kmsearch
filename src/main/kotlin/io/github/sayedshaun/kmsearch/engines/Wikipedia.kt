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
import org.jsoup.Jsoup

/** Wikipedia's official search API: free, keyless and meant for apps. [baseUrl] overrides `https://<lang>.wikipedia.org` (for tests or a mirror). */
public class WikipediaEngine(
    private val http: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String? = null,
) : Engine {
    override val id: String = "wikipedia"

    @Serializable private class Reply(val query: Query? = null)
    @Serializable private class Query(val search: List<Hit> = emptyList())
    @Serializable private class Hit(val title: String, val snippet: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery): List<EngineResult> {
        val lang = query.language.substringBefore('-').lowercase().takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        val base = (baseUrl ?: "https://$lang.wikipedia.org").trimEnd('/')
        val limit = query.limit.coerceIn(1, 20)
        val url = "$base/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("list", "search")
            .addQueryParameter("srsearch", query.text).addQueryParameter("srlimit", limit.toString())
            .addQueryParameter("sroffset", ((query.page - 1).coerceAtLeast(0) * limit).toString())
            .addQueryParameter("format", "json").addQueryParameter("utf8", "1")
            .build()
        val body = http.fetch(Request.Builder().url(url).header("User-Agent", userAgent).build())
        val reply = try { json.decodeFromString<Reply>(body) } catch (e: Exception) { throw EngineException("sent an unreadable answer", cause = e) }
        return reply.query?.search.orEmpty().map {
            val page = it.title.replace(' ', '_')
            val link = "$base/wiki/".toHttpUrl().newBuilder().addPathSegment(page).build().toString()
            EngineResult(it.title, link, Jsoup.parse(it.snippet).text())
        }
    }
}
