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
import io.thelandscape.krawler.crawler.History.KrawlHistoryIf
import io.thelandscape.krawler.crawler.KrawlConfig
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueIf
import io.thelandscape.krawler.crawler.KrawlQueue.QueueEntry
import io.thelandscape.krawler.crawler.Krawler
import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestProviderIf
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.test.assertEquals

class MockQueue : KrawlQueueIf {
    val back: MutableList<QueueEntry> = mutableListOf()

    override fun pop(): QueueEntry? {
        return if (back.size > 0) back.removeAt(0) else null
    }

    override fun push(urls: List<QueueEntry>): List<QueueEntry> {
        urls.forEach { back.add(back.size, it) }
        return urls
    }

}

class KrawlerTest {

    val mockConfig = KrawlConfig(emptyQueueWaitTime = 1)
    val mockQueue = MockQueue()
    val mockHistory = mock<KrawlHistoryIf>()
    val mockRequests = mock<RequestProviderIf>()
    val mockThreadfactory = mock<ThreadFactory>()
    val mockThreadpool = Executors.newCachedThreadPool(mockThreadfactory)

    val preparedResponse = KrawlDocument(prepareResponse(200, ""))

    class testCrawler(x: KrawlConfig, y: KrawlQueueIf,
                      w: KrawlHistoryIf, v: RequestProviderIf, z: ExecutorService): Krawler(x, y, w, v, z) {
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

    val testKrawler = testCrawler(mockConfig, mockQueue, mockHistory, mockRequests, mockThreadpool)

    @Before fun setUp() {
        MockitoKotlin.registerInstanceCreator { KrawlUrl.new("") }
    }

    /**
     * Test that the seed URL is added to the krawl queue and that the threadpool is started
     */
    @Test fun testStartBlocking() {
        val url: List<String> = listOf("http://www.test.com/")
        testKrawler.startNonblocking(url)

        val qe: QueueEntry = mockQueue.back.first()
        assertEquals("http://www.test.com/", qe.url)

        // Verify submit gets called on the threadpool the number of times specified in the config
        verify(mockThreadfactory, times(mockConfig.numThreads)).newThread(any())
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
        // Insert some stuff into the queue
        mockQueue.push(listOf(QueueEntry("http://www.test.com")))

        // Make the hasBeenSeen return true
        whenever(mockHistory.hasBeenSeen(any())).thenReturn(false)
        // Make sure we get a request response
        whenever(mockRequests.getUrl(any())).thenReturn(preparedResponse)

        // Run doCrawl
        testKrawler.doCrawl()

        // Ensure we've called to verify this is a unique URL
        verify(mockHistory).hasBeenSeen(any())
        // Now verify that we insert the URL to the history
        verify(mockHistory).insert(any())

        // The global visit count should also be 1
        assertEquals(1, testKrawler.visitCount)
    }

}