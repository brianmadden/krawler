package io.thelandscape

/**
 * Created by brian.a.madden@gmail.com on 12/4/16.
 *
 * Copyright (c) <2016> <H, llc>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import com.nhaarman.mockito_kotlin.MockitoKotlin
import com.nhaarman.mockito_kotlin.mock
import io.thelandscape.krawler.crawler.History.KrawlHistoryEntry
import io.thelandscape.krawler.crawler.History.KrawlHistoryIf
import io.thelandscape.krawler.crawler.KrawlConfig
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueEntry
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueIf
import io.thelandscape.krawler.crawler.Krawler
import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestProviderIf
import io.thelandscape.krawler.robots.RoboMinderIf
import io.thelandscape.krawler.robots.RobotsConfig
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.apache.http.client.protocol.HttpClientContext
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KrawlerTest {

    val exampleUrl = KrawlUrl.new("http://www.example.org")
    val mockConfig = KrawlConfig(emptyQueueWaitTime = 1, totalPages = 1, maxDepth = 4)
    val mockHistory = mock<KrawlHistoryIf>()
    val mockQueue = listOf(mock<KrawlQueueIf>())
    val mockRequests = mock<RequestProviderIf>()
    val mockJob = Job()
    val mockMinder = mock<RoboMinderIf>()
    val mockContext = mock<HttpClientContext>()

    val preparedResponse = KrawlDocument(exampleUrl,
            prepareResponse(200, "<html><head><title>Test</title></head><body>" +
                    "<div><a href=\"http://www.testone.com\">Test One</a>" +
                    "<img src=\"imgone.jpg\" /></div></body></html>"),
            mockContext)

    class testCrawler(x: KrawlConfig,
                      w: KrawlHistoryIf,
                      y: List<KrawlQueueIf>,
                      u: RobotsConfig?,
                      v: RequestProviderIf,
                      z: Job): Krawler(x, w, y, u, v, z) {

        val capture: MutableList<String> = mutableListOf()

        override fun shouldVisit(url: KrawlUrl): Boolean {
            return true
        }

        override fun shouldCheck(url: KrawlUrl): Boolean {
            return false
        }

        override fun visit(url: KrawlUrl, doc: KrawlDocument) {
            capture.add("VISIT - ${url.rawUrl}")
        }

        override fun check(url: KrawlUrl, statusCode: Int) {
            capture.add("CHECK - ${url.rawUrl}")
        }

    }

    val testKrawler = testCrawler(mockConfig, mockHistory, mockQueue, null, mockRequests, mockJob)

    @Before fun setUp() {
        MockitoKotlin.registerInstanceCreator { KrawlUrl.new("") }
        testKrawler.minder = mockMinder
        testKrawler.capture.clear()
    }

    /**
     * Test the doCrawl method
     */

    @Test fun testDoCrawl() = runBlocking {
        val allThree = produce(CommonPool) {
            for (a in listOf(Krawler.KrawlAction.Noop(),
                    Krawler.KrawlAction.Visit(exampleUrl, preparedResponse),
                    Krawler.KrawlAction.Check(exampleUrl, preparedResponse.statusCode)))
                send(a)
        }

        testKrawler.doCrawl(allThree)
        assertTrue(testKrawler.capture.contains("VISIT - http://www.example.org"))
        assertTrue(testKrawler.capture.contains("CHECK - http://www.example.org"))
        assertEquals(2, testKrawler.capture.size)
    }

    /**
     * TODO: Add this test when there is better support for coroutines in mockito
    @Test fun testFetch() = runBlocking {

        whenever(mockMinder.isSafeToVisit(any())).thenReturn(true)

        // Depth tests (max depth was set to 4)
        var resp = testKrawler.fetch(exampleUrl, 5, exampleUrl).await()
        assertTrue { resp is Krawler.KrawlAction.Noop }

        resp = testKrawler.fetch(exampleUrl, 3, exampleUrl).await()

        assertTrue { resp is Krawler.KrawlAction.Visit }
    }
    */

    @Test fun testHarvestLinks() {
        val links: List<KrawlQueueEntry> =
                runBlocking { testKrawler.harvestLinks(preparedResponse, exampleUrl, KrawlHistoryEntry(), 0, 0) }

        assertEquals(2, links.size)
        val linksText = links.map { it.url }
        assertTrue { "http://www.testone.com/" in linksText }
        assertTrue { "http://www.example.org/imgone.jpg" in linksText }
    }
}