package io.github.sayedshaun.kmsearch.engines

import io.github.sayedshaun.kmsearch.DEFAULT_USER_AGENT
import io.github.sayedshaun.kmsearch.Engine
import io.github.sayedshaun.kmsearch.EngineException
import io.github.sayedshaun.kmsearch.EngineResult
import io.github.sayedshaun.kmsearch.SearchQuery
import io.github.sayedshaun.kmsearch.fetch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Mojeek, an independent search index, read from its HTML results page. Mojeek shows an unrecognised client a JavaScript challenge
 * (seen in testing), which this reports as a blocked engine, so it is not in [io.github.sayedshaun.kmsearch.MetaSearch.default].
 * Mojeek sells an official API, which is the right way to use it in a shipped app.
 */
public class MojeekEngine(
    private val http: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String = "https://www.mojeek.com",
) : Engine {
    override val id: String = "mojeek"

    override suspend fun search(query: SearchQuery): List<EngineResult> {
        val url = "${baseUrl.trimEnd('/')}/search".toHttpUrl().newBuilder().addQueryParameter("q", query.text).apply {
            if (query.page > 1) addQueryParameter("s", ((query.page - 1) * 10 + 1).toString())
        }.build()
        return parse(http.fetch(Request.Builder().url(url).header("User-Agent", userAgent).build()))
    }

    internal fun parse(html: String): List<EngineResult> {
        val doc = Jsoup.parse(html)
        // Mojeek answers a client it does not know with a JavaScript challenge instead of results
        if (doc.title().contains("captcha", ignoreCase = true) || doc.text().contains("JavaScript is required to complete this challenge")) {
            throw EngineException("asked for a bot check", blocked = true)
        }
        return doc.select("ul.results-standard > li").mapNotNull { li ->
            val a = li.selectFirst("a.title") ?: return@mapNotNull null
            val link = a.absUrl("href").ifBlank { a.attr("href") }
            if (!link.startsWith("http")) return@mapNotNull null
            EngineResult(a.text(), link, li.selectFirst("p.s")?.text().orEmpty())
        }
    }
}
