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
 * OpenStreetMap's Nominatim geocoder (keyless): finds places, with the address as the snippet and the OpenStreetMap page as the link.
 * Results are places, not web pages, so it is not in [io.github.sayedshaun.kmsearch.MetaSearch.default]; add it for a travel or maps app.
 * Follow the [usage policy](https://operations.osmfoundation.org/policies/nominatim/): at most one request a second, a real user agent
 * (set `userAgent` to your app's), and show "© OpenStreetMap contributors" (ODbL) where you show its results.
 */
public class NominatimEngine(
    private val http: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
) : Engine {
    override val id: String = "nominatim"

    @Serializable private class Hit(val osm_type: String = "", val osm_id: Long = 0, val name: String = "", val display_name: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery): List<EngineResult> {
        val url = "${baseUrl.trimEnd('/')}/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.text).addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", query.limit.coerceIn(1, 20).toString())
            .addQueryParameter("accept-language", query.language).build()
        val body = http.fetch(Request.Builder().url(url).header("User-Agent", userAgent).build())
        val hits = try { json.decodeFromString<List<Hit>>(body) } catch (e: Exception) { throw EngineException("sent an unreadable answer", cause = e) }
        return hits.filter { it.osm_type in setOf("node", "way", "relation") && it.osm_id != 0L }.map {
            val title = it.name.ifBlank { it.display_name.substringBefore(',') }
            EngineResult(title, "https://www.openstreetmap.org/${it.osm_type}/${it.osm_id}", it.display_name)
        }
    }
}
