# kmsearch

A small, on-device **metasearch library for Kotlin/JVM and Android**. It asks several search engines at once, merges and
de-duplicates the answers, caches them, and backs off from engines that block it.

It is meant to run **on the user's own phone**: every user queries the engines from their own address, so there is no
server to host and no shared rate limit.

> Not affiliated with SearXNG. This is an independent, from-scratch implementation under Apache-2.0 and contains no SearXNG code.

```kotlin
val http = OkHttpClient()
val search = MetaSearch.default(http) // Wikipedia + DuckDuckGo

val response = search.search(SearchQuery("best time to visit Sylhet", language = "en"))
response.results.forEach { println("${it.title}  ${it.url}  (${it.engines})") }
response.statuses.filter { !it.ok }.forEach { println("${it.engineId}: ${it.error}") }
```

## What it does
- **Parallel fan-out** with a per-engine timeout; one slow or failing engine never loses the others' results.
- **Merge** by normalised URL (ignores `www.`, scheme, trailing slash, `utm_*`), ranked by reciprocal rank fusion, so a page several engines agree on comes first.
- **Suspension:** an engine that fails is skipped for a while (longer if it was blocked or rate limited).
- **Cache:** in-memory LRU with a TTL; pass `cache = null` to turn it off.
- **Status per engine** in every response, so your UI can say which sources failed.

## Engines
| Engine | Source | Notes |
|---|---|---|
| `WikipediaEngine` | official API | keyless, stable |
| `DuckDuckGoEngine` | HTML page | no official API; can be blocked or change. Check their terms |
| `MojeekEngine` | HTML page | answers unknown clients with a JavaScript challenge, so it reports "blocked" and is not in `MetaSearch.default`. Use Mojeek's official API in an app |

Add your own by implementing `Engine` (`id` and `suspend fun search(query): List<EngineResult>`).
HTML engines break when sites change; treat them as best-effort and prefer official APIs.

## User agent
Wikimedia asks clients to identify themselves. Pass your app's own `userAgent` to each engine.

## Build
```
./gradlew test
```

## Licence
Apache-2.0, see [LICENSE](LICENSE).
