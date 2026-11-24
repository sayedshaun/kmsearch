package io.github.sayedshaun.kmsearch

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    @Test fun `same page spelled differently has one key`() {
        val a = normalizeUrl("https://www.Example.com/a/b/?utm_source=x&b=2&a=1#top")
        val b = normalizeUrl("http://example.com/a/b?a=1&b=2")
        assertEquals(a, b)
    }

    @Test fun `a page two engines return beats one engine's first place`() {
        val merged = merge(
            listOf(
                "a" to listOf(EngineResult("Solo", "https://solo.test/"), EngineResult("Shared", "https://shared.test/")),
                "b" to listOf(EngineResult("Shared page", "https://www.shared.test", "longer snippet")),
            ),
            limit = 10,
        )
        assertEquals(listOf("Shared page", "Solo"), merged.map { it.title })
        assertEquals(listOf("a", "b"), merged.first().engines)
        assertEquals("longer snippet", merged.first().snippet)
        assertEquals("Shared page", merged.first().title)
    }

    @Test fun `limit and blank entries`() {
        val merged = merge(listOf("a" to listOf(EngineResult("", "https://x.test"), EngineResult("T1", "https://1.test"), EngineResult("T2", "https://2.test"))), limit = 1)
        assertEquals(listOf("T1"), merged.map { it.title })
    }
}
