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

/** Wikidata's official entity search (keyless, CC0): a name and a one-line description of a thing, person or place. Good for "what is X". */
public class WikidataEngine(
    private val http: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String = "https://www.wikidata.org",
) : Engine {
    override val id: String = "wikidata"

    @Serializable private class Reply(val search: List<Hit> = emptyList())
    @Serializable private class Hit(val id: String, val label: String = "", val description: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery): List<EngineResult> {
        val base = baseUrl.trimEnd('/')
        val url = "$base/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "wbsearchentities").addQueryParameter("search", query.text)
            .addQueryParameter("language", query.language.substringBefore('-').lowercase()).addQueryParameter("uselang", query.language.substringBefore('-').lowercase())
            .addQueryParameter("limit", query.limit.coerceIn(1, 20).toString())
            .addQueryParameter("continue", ((query.page - 1).coerceAtLeast(0) * query.limit.coerceIn(1, 20)).toString())
            .addQueryParameter("format", "json").build()
        val body = http.fetch(Request.Builder().url(url).header("User-Agent", userAgent).build())
        val reply = try { json.decodeFromString<Reply>(body) } catch (e: Exception) { throw EngineException("sent an unreadable answer", cause = e) }
        return reply.search.filter { it.label.isNotBlank() }.map { EngineResult(it.label, "$base/wiki/${it.id}", it.description) }
    }
}
