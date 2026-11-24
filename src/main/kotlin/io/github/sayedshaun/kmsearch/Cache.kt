package io.github.sayedshaun.kmsearch

/** A small in-memory LRU cache whose entries expire after [ttlMillis]. Thread-safe. */
public class ResultCache(
    private val maxEntries: Int = 64,
    private val ttlMillis: Long = 24 * 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val value: SearchResponse, val expiresAt: Long)

    private val map = object : LinkedHashMap<SearchQuery, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchQuery, Entry>) = size > maxEntries
    }

    public fun get(query: SearchQuery): SearchResponse? = synchronized(map) {
        val e = map[query] ?: return null
        if (e.expiresAt <= now()) { map.remove(query); null } else e.value
    }

    public fun put(query: SearchQuery, response: SearchResponse) {
        synchronized(map) { map[query] = Entry(response, now() + ttlMillis) }
    }

    public fun clear() { synchronized(map) { map.clear() } }
}
