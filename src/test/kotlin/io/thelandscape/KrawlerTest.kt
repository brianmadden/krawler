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

import com.nhaarman.mockito_kotlin.*
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
import org.apache.http.client.protocol.HttpClientContext
import org.junit.Before
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KrawlerTest {

    val exampleUrl = KrawlUrl.new("http://www.example.org")
    val mockConfig = KrawlConfig(emptyQueueWaitTime = 1)
    val mockHistory = mock<KrawlHistoryIf>()
    val mockQueue = mock<KrawlQueueIf>()
    val mockRequests = mock<RequestProviderIf>()
    val mockThreadFactory = mock<ThreadFactory>()
    val threadpool: ThreadPoolExecutor =
            ThreadPoolExecutor(4, 4, 1000L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>(), mockThreadFactory)
    val mockThreadpool = mock<ThreadPoolExecutor>()
    val mockMinder = mock<RoboMinderIf>()
    val mockContext = mock<HttpClientContext>()

    val preparedResponse = KrawlDocument(exampleUrl,
            prepareResponse(200, "<html><head><title>Test</title></head><body>" +
                    "<div><a href=\"http://www.testone.com\">Test One</a>" +
                    "<img src=\"imgone.jpg\" /></div></body></html>"),
            mockContext)

    class testCrawler(x: KrawlConfig,
                      w: KrawlHistoryIf,
                      y: KrawlQueueIf,
                      u: RobotsConfig?,
                      v: RequestProviderIf,
                      z: ThreadPoolExecutor): Krawler(x, w, y, u, v, z) {
        override fun shouldVisit(url: KrawlUrl): Boolean {
            return true
        }

        override fun shouldCheck(url: KrawlUrl): Boolean {
            return false
        }

        override fun visit(url: KrawlUrl, doc: KrawlDocument) {
        }

        override fun check(url: KrawlUrl, statusCode: Int) {
        }

    }

    val realThreadpoolTestKrawler = testCrawler(mockConfig, mockHistory, mockQueue, null, mockRequests, threadpool)
    val mockThreadpoolTestKrawler = testCrawler(mockConfig, mockHistory, mockQueue, null, mockRequests, mockThreadpool)

    @Before fun setUp() {
        MockitoKotlin.registerInstanceCreator { KrawlUrl.new("") }
        realThreadpoolTestKrawler.minder = mockMinder
    }

    /**
     * Test that the seed URL is added to the krawl queue and that the threadpool is started
     */
    @Test fun testStartBlocking() {
        val url: List<String> = listOf("1", "2", "3", "4")
        whenever(mockThreadpool.isTerminated).thenReturn(true)
        mockThreadpoolTestKrawler.start(url)

        // Verify submit gets called on the threadpool for each of the URLs
        verify(mockThreadpool, times(url.size)).submit(any())
    }

    /**
     * Test that when stop is called we try to shutdown
     */
    @Test fun testStop() {
        mockThreadpoolTestKrawler.stop()
        verify(mockThreadpool).shutdown()
    }

    /**
     * Test that when shutdown is called we try to shutdownNow
     */
    @Test fun testShutdown() {
        mockThreadpoolTestKrawler.shutdown()
        verify(mockThreadpool, atLeastOnce()).shutdownNow()
    }

    /**
     * Test the doCrawl method
     */

    @Test fun testDoCrawl() {
        // Make the hasBeenSeen return true
        whenever(mockHistory.hasBeenSeen(any())).thenReturn(false)
        whenever(mockHistory.insert(any())).thenReturn(KrawlHistoryEntry())
        // Make sure we get a request response
        whenever(mockRequests.getUrl(any())).thenReturn(preparedResponse)
        // Make robo minder return true
        whenever(mockMinder.isSafeToVisit(any())).thenReturn(true)

        // Get it started
        realThreadpoolTestKrawler.doCrawl(KrawlQueueEntry("http://www.test.com"))

        // Verify that isSafeToVisit was called, minding robots.txt
        verify(mockMinder).isSafeToVisit(KrawlUrl.new("http://www.test.com"))
        // Ensure we've called to verify this is a unique URL
        verify(mockHistory).hasBeenSeen(any())
        // Now verify that we insert the URL to the history
        verify(mockHistory).insert(any())

        // The global visit count should also be 1
        assertEquals(1, realThreadpoolTestKrawler.visitCount)
    }

    @Test fun testHarvestLinks() {
        val links: List<KrawlQueueEntry> =
                mockThreadpoolTestKrawler.harvestLinks(preparedResponse, exampleUrl, KrawlHistoryEntry(), 0)

        assertEquals(2, links.size)
        val linksText = links.map { it.url }
        assertTrue { "http://www.testone.com/" in linksText }
        assertTrue { "http://www.example.org/imgone.jpg" in linksText }
    }
}