package io.github.sayedshaun.kmsearch.engines

import io.github.sayedshaun.kmsearch.DEFAULT_USER_AGENT
import io.github.sayedshaun.kmsearch.Engine
import io.github.sayedshaun.kmsearch.EngineException
import io.github.sayedshaun.kmsearch.EngineResult
import io.github.sayedshaun.kmsearch.SearchQuery
import io.github.sayedshaun.kmsearch.fetch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * DuckDuckGo's HTML page (`html.duckduckgo.com/html/`). There is no official API, so this reads the page and can break when the
 * page changes or when DuckDuckGo shows a bot check; either way it fails cleanly and [io.github.sayedshaun.kmsearch.MetaSearch] suspends it.
 * Check DuckDuckGo's terms before you ship it in an app.
 */
public class DuckDuckGoEngine(
    private val http: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String = "https://html.duckduckgo.com",
) : Engine {
    override val id: String = "duckduckgo"

    override suspend fun search(query: SearchQuery): List<EngineResult> {
        val url = "${baseUrl.trimEnd('/')}/html/".toHttpUrl().newBuilder().addQueryParameter("q", query.text).apply {
            if (query.page > 1) addQueryParameter("s", ((query.page - 1) * 30).toString())
        }.build()
        val html = http.fetch(Request.Builder().url(url).header("User-Agent", userAgent).build())
        return parse(html)
    }

    internal fun parse(html: String): List<EngineResult> {
        val doc = Jsoup.parse(html)
        if (doc.selectFirst("form#challenge-form, .anomaly-modal__modal") != null) {
            throw EngineException("asked for a bot check", blocked = true)
        }
        return doc.select("div.result").filterNot { it.hasClass("result--ad") }.mapNotNull { row ->
            val a = row.selectFirst("a.result__a") ?: return@mapNotNull null
            val link = unwrap(a.attr("href")) ?: return@mapNotNull null
            EngineResult(a.text(), link, row.selectFirst(".result__snippet")?.text().orEmpty())
        }
    }

    /** DuckDuckGo links go through `//duckduckgo.com/l/?uddg=<real address>`; this returns the real address. */
    private fun unwrap(href: String): String? {
        val absolute = if (href.startsWith("//")) "https:$href" else href
        val u = absolute.toHttpUrlOrNull() ?: return null
        return if (u.host.endsWith("duckduckgo.com") && u.encodedPath.startsWith("/l/")) u.queryParameter("uddg")
        else absolute
    }
}
