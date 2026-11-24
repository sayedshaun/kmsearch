package io.github.sayedshaun.kmsearch.engines

import io.github.sayedshaun.kmsearch.EngineException
import io.github.sayedshaun.kmsearch.SearchQuery
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EnginesTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient()
    private val base = server.url("/").toString().trimEnd('/')

    @After fun stop() = server.shutdown()

    @Test fun `wikipedia maps hits to pages and strips snippet markup`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"query":{"search":[{"title":"Sylhet Division","snippet":"a <span class=\"searchmatch\">division</span> of Bangladesh"}]}}"""))
        val r = WikipediaEngine(http, baseUrl = base).search(SearchQuery("sylhet"))
        assertEquals("Sylhet Division", r[0].title)
        assertEquals("$base/wiki/Sylhet_Division", r[0].url)
        assertEquals("a division of Bangladesh", r[0].snippet)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("srsearch=sylhet"))
        assertTrue(req.getHeader("User-Agent")!!.startsWith("kmsearch"))
    }

    @Test fun `wikipedia with no hits is empty, with junk is an engine failure`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"query":{"search":[]}}"""))
        assertEquals(0, WikipediaEngine(http, baseUrl = base).search(SearchQuery("zzzz")).size)
        server.enqueue(MockResponse().setBody("not json"))
        try { WikipediaEngine(http, baseUrl = base).search(SearchQuery("x1")); fail() } catch (e: EngineException) { assertTrue(!e.blocked) }
    }

    private val ddgHtml = """
        <div class="result results_links"><h2><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1&amp;rut=abc">Example A</a></h2>
          <a class="result__snippet">First snippet</a></div>
        <div class="result result--ad"><a class="result__a" href="https://ad.test/">Ad</a></div>
        <div class="result"><a class="result__a" href="https://direct.test/">Direct</a></div>
    """.trimIndent()

    @Test fun `duckduckgo unwraps redirect links and skips ads`() {
        val r = DuckDuckGoEngine(http, baseUrl = base).parse(ddgHtml)
        assertEquals(listOf("https://example.com/a?x=1", "https://direct.test/"), r.map { it.url })
        assertEquals("First snippet", r[0].snippet)
    }

    @Test fun `duckduckgo bot check and rate limit are blocked failures`() = runBlocking {
        try { DuckDuckGoEngine(http, baseUrl = base).parse("""<div class="anomaly-modal__modal">x</div>"""); fail() } catch (e: EngineException) { assertTrue(e.blocked) }
        server.enqueue(MockResponse().setResponseCode(429))
        try { DuckDuckGoEngine(http, baseUrl = base).search(SearchQuery("kotlin")); fail() } catch (e: EngineException) { assertTrue(e.blocked) }
    }

    @Test fun `mojeek javascript challenge is a blocked failure`() {
        try { MojeekEngine(http, baseUrl = base).parse("<html><head><title>Captcha</title></head><body>JavaScript is required to complete this challenge.</body></html>"); fail() } catch (e: EngineException) { assertTrue(e.blocked) }
    }

    @Test fun `mojeek parses its result list`() = runBlocking {
        server.enqueue(MockResponse().setBody("""<ul class="results-standard"><li><h2><a class="title" href="https://k.test/x">Kotlin</a></h2><p class="s">A language</p></li><li><a class="title" href="/relative">skip</a></li></ul>"""))
        val r = MojeekEngine(http, baseUrl = base).search(SearchQuery("kotlin", page = 2))
        assertEquals(listOf("https://k.test/x"), r.map { it.url })
        assertEquals("A language", r[0].snippet)
        assertTrue(server.takeRequest().path!!.contains("s=11"))
    }
}
