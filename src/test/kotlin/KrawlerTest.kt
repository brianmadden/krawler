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
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueIf
import io.thelandscape.krawler.crawler.KrawlQueue.QueueEntry
import io.thelandscape.krawler.crawler.Krawler
import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import org.junit.Test
import java.util.concurrent.ExecutorService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KrawlerTest {

    val mockConfig = KrawlConfig(emptyQueueWaitTime = 1)
    val mockQueue = mock<KrawlQueueIf>()
    val mockHistory = mock<KrawlHistoryIf>()
    val mockThreadpool = mock<ExecutorService>()

    class testCrawler(x: KrawlConfig, y: KrawlQueueIf,
                      w: KrawlHistoryIf, z: ExecutorService): Krawler(x, y, w, z) {
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

    val testKrawler = testCrawler(mockConfig, mockQueue, mockHistory, mockThreadpool)

    /**
     * Test that the seed URL is added to the krawl queue and that the threadpool is started
     */
    @Test fun testStarts() {
        val url: List<String> = listOf("http://www.test.com/")
        testKrawler.start(url)

        // Make sure that the URL gets pushed to the crawl queue
        argumentCaptor<List<QueueEntry>>().apply {
            verify(mockQueue).push(capture())

            val qe: QueueEntry = allValues[0][0]
            assertEquals("http://www.test.com/", qe.url)
        }

        // Verify submit gets called on the threadpool the number of times specified in the config
        verify(mockThreadpool, times(mockConfig.numThreads)).submit(any())

    }

    /**
     * Test that when stop is called we try to shutdown
     */
    @Test fun testStop() {
        testKrawler.stop()
        verify(mockThreadpool).shutdown()
    }

    /**
     * Test that when shutdown is called we try to shutdownNow
     */
    @Test fun testShutdown() {
        testKrawler.shutdown()
        verify(mockThreadpool).shutdownNow()
    }

    /**
     * Test the doCrawl method
     */
    @Test fun testDoCrawl() {
        testKrawler.doCrawl()

        // doCrawl should...

        // pop a URL From the queue 1 (initial time) + config.emptyQueueWaitTime (1 / second for the duration)
        verify(mockQueue, times(1 + mockConfig.emptyQueueWaitTime)).pop()
        // Since the queue didn't return anything, the crawler is now shutdown

        // Force the queue to return something
        whenever(mockQueue.pop()).thenReturn(QueueEntry("http://www.test.com/"))

        // Run doCrawl again
        testKrawler.doCrawl()

        // pop a URL from the queue 1 time
        verify(mockQueue).pop()

        // Now that it has been popped, ensure that we don't get it again (emulate the delete)
        // otherwise we'll just keep crawling until we go OOM
        whenever(mockQueue.pop()).thenReturn(null)

        val kUrl: KrawlUrl = KrawlUrl.Companion.new("http://www.test.com/")
        // Make the verifyUnique return true
        whenever(mockHistory.verifyUnique(kUrl)).thenReturn(true)
        // Ensure we've called to verify this is a unique URL
        verify(mockHistory).verifyUnique(kUrl)
        // Now verify that we insert the URL to the history
        verify(mockHistory).verifyUnique(kUrl)

        // Should visit will have been called, and since it just returns true
        // we should see the domain count for http://www.test.com/ should be 1
        assertEquals(1, testKrawler.domainVisitCounts["http://www.test.com/"])
        // The global visit count should also be 1
        assertEquals(1, testKrawler.globalVisitCount)

    }

}