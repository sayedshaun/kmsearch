package io.github.sayedshaun.kmsearch

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val RRF_K = 60.0

private val TRACKING_PARAMS = setOf("fbclid", "gclid", "msclkid", "mc_cid", "mc_eid", "ref", "ref_src", "igshid")

/**
 * A key that is the same for the same page however an engine spelled it: scheme, a leading "www.", a trailing slash,
 * the fragment and tracking parameters are ignored, and query parameters are sorted.
 */
public fun normalizeUrl(url: String): String {
    val u = url.trim().toHttpUrlOrNull() ?: return url.trim().lowercase()
    val host = u.host.removePrefix("www.")
    val path = u.encodedPath.trimEnd('/')
    val query = u.queryParameterNames
        .filterNot { it.startsWith("utm_") || it in TRACKING_PARAMS }
        .sorted()
        .joinToString("&") { "$it=${u.queryParameter(it).orEmpty()}" }
    return if (query.isEmpty()) host + path else "$host$path?$query"
}

/**
 * Merges each engine's ranked list into one: reciprocal rank fusion, so a page ranked well by several engines beats one
 * ranked first by a single engine. Ties keep the order the engines were given in. The kept title and snippet are the longest seen.
 */
internal fun merge(perEngine: List<Pair<String, List<EngineResult>>>, limit: Int): List<SearchResult> {
    class Acc(var title: String, var url: String, var snippet: String, var date: String?) {
        val engines = LinkedHashSet<String>()
        var score = 0.0
    }
    val byKey = LinkedHashMap<String, Acc>()
    for ((engineId, results) in perEngine) {
        results.forEachIndexed { rank, r ->
            if (r.url.isBlank() || r.title.isBlank()) return@forEachIndexed
            val acc = byKey.getOrPut(normalizeUrl(r.url)) { Acc(r.title, r.url, r.snippet, r.publishedDate) }
            if (engineId in acc.engines) return@forEachIndexed // the same engine listing a page twice counts once
            acc.engines += engineId
            acc.score += 1.0 / (RRF_K + rank + 1)
            if (r.snippet.length > acc.snippet.length) acc.snippet = r.snippet
            if (r.title.length > acc.title.length) acc.title = r.title
            if (acc.date == null) acc.date = r.publishedDate
        }
    }
    return byKey.values
        .sortedByDescending { it.score }
        .take(limit.coerceAtLeast(1))
        .map { SearchResult(it.title, it.url, it.snippet, it.engines.toList(), it.score, it.date) }
}
