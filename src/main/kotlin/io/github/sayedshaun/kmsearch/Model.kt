package io.github.sayedshaun.kmsearch

/** What to search for. [language] is a BCP-47 tag such as "en"; [page] starts at 1. */
public data class SearchQuery(
    val text: String,
    val language: String = "en",
    val page: Int = 1,
    val limit: Int = 10,
)

/** One result as a single engine returned it, before merging. */
public data class EngineResult(
    val title: String,
    val url: String,
    val snippet: String = "",
    val publishedDate: String? = null,
)

/** One merged result: the same page from several engines is one entry, ranked higher the more engines agree. */
public data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    /** the ids of every engine that returned this page */
    val engines: List<String>,
    val score: Double,
    val publishedDate: String? = null,
)

/** How one engine did on one search, so an app can say which sources failed. */
public data class EngineStatus(
    val engineId: String,
    val ok: Boolean,
    val resultCount: Int = 0,
    val tookMillis: Long = 0,
    /** a short reason, never containing a key or a full URL; null when [ok] */
    val error: String? = null,
    /** true when the engine was skipped because an earlier failure suspended it */
    val suspended: Boolean = false,
)

public data class SearchResponse(
    val query: SearchQuery,
    val results: List<SearchResult>,
    val statuses: List<EngineStatus>,
    val fromCache: Boolean = false,
) {
    /** true when at least one engine answered, even if it found nothing */
    val anyEngineAnswered: Boolean get() = statuses.any { it.ok }
}

/** One search source. Implementations throw [EngineException] for a failure that should suspend them. */
public interface Engine {
    /** stable id, shown in [SearchResult.engines] and [EngineStatus.engineId] */
    public val id: String

    /** results in the engine's own order, best first */
    public suspend fun search(query: SearchQuery): List<EngineResult>
}

/** An engine failed: blocked, rate-limited, unreachable or sent something unreadable. The message is safe to show. */
public class EngineException(message: String, public val blocked: Boolean = false, cause: Throwable? = null) :
    RuntimeException(message, cause)
