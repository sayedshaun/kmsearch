package io.github.sayedshaun.kmsearch

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaSearchTest {
    private class Fake(override val id: String, val action: suspend () -> List<EngineResult>) : Engine {
        var calls = 0
        override suspend fun search(query: SearchQuery): List<EngineResult> { calls++; return action() }
    }

    private val q = SearchQuery("kotlin")
    private fun ok(vararg urls: String) = urls.map { EngineResult("T $it", "https://$it.test") }

    @Test fun `a failing engine does not stop the others and is reported`() = runBlocking {
        val good = Fake("good") { ok("a", "b") }
        val bad = Fake("bad") { throw EngineException("blocked", blocked = true) }
        val r = MetaSearch(listOf(good, bad), cache = null).search(q)
        assertEquals(2, r.results.size)
        assertEquals(listOf(true, false), r.statuses.map { it.ok })
        assertEquals("blocked", r.statuses[1].error)
    }

    @Test fun `a slow engine times out`() = runBlocking {
        val slow = Fake("slow") { delay(5_000); ok("x") }
        val fast = Fake("fast") { ok("y") }
        val r = MetaSearch(listOf(slow, fast), engineTimeoutMillis = 100, cache = null).search(q)
        assertEquals(listOf("T y"), r.results.map { it.title })
        assertEquals("timed out", r.statuses[0].error)
    }

    @Test fun `a failed engine is skipped until its suspension ends`() = runBlocking {
        var clock = 0L
        val flaky = Fake("flaky") { throw EngineException("down") }
        val other = Fake("other") { ok("z") }
        val meta = MetaSearch(listOf(flaky, other), suspendMillis = 1_000, cache = null, now = { clock })
        meta.search(q)
        val second = meta.search(q)
        assertEquals(1, flaky.calls)
        assertTrue(second.statuses[0].suspended)
        clock = 1_500
        meta.search(q)
        assertEquals(2, flaky.calls)
    }

    @Test fun `answers are cached and an empty one is not`() = runBlocking {
        val e = Fake("e") { ok("a") }
        val meta = MetaSearch(listOf(e))
        assertFalse(meta.search(q).fromCache)
        assertTrue(meta.search(q).fromCache)
        assertEquals(1, e.calls)

        val empty = Fake("empty") { emptyList() }
        val m2 = MetaSearch(listOf(empty))
        m2.search(q); m2.search(q)
        assertEquals(2, empty.calls)
    }

    @Test fun `a short query searches nothing`() = runBlocking {
        val e = Fake("e") { ok("a") }
        val r = MetaSearch(listOf(e)).search(SearchQuery(" a "))
        assertTrue(r.results.isEmpty())
        assertEquals(0, e.calls)
        assertNotNull(r.query)
    }
}
