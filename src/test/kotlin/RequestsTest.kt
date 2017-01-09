/**
 * Created by brian.a.madden@gmail.com on 10/23/16.
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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import io.thelandscape.krawler.crawler.KrawlConfig
import io.thelandscape.krawler.http.ContentFetchError
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.Requests
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.test.assertTrue

class RequestsTest {

    val mockHttpClient = mock<CloseableHttpClient> {}
    val config: KrawlConfig = KrawlConfig()
    val request: Requests = Requests(config, mockHttpClient)
    val testUrl = KrawlUrl.new("http://httpbin.org")
    val testUrl2 = KrawlUrl.new("http://httpbin.org/get")

    @Test fun testRequestCheck() {
        try {
            request.checkUrl(testUrl)
        } catch (e: ContentFetchError) {
            // Ignore this, it's expected
        }
        // it should call execute once with an HttpHead
        // TODO: Swap the any() call to HttpHead somehow
        verify(mockHttpClient, times(1)).execute(any())
    }

    @Test fun testRequestGet() {

        val start = Instant.now().toEpochMilli()
        val threadpool: ExecutorService = Executors.newFixedThreadPool(4)
        val numTimes = 5
        (1 .. numTimes).forEach {
            try {
                request.getUrl(testUrl)
            } catch (e: ContentFetchError) {
                // Ignore this, it's expected
            }
        }
        threadpool.shutdown()
        while(!threadpool.isTerminated) {}

        val end = Instant.now().toEpochMilli()

        // Make sure that the politeness delay is respected
        // This delay should take politeness delay * (n - 1) requests (first doesn't wait)
        assertTrue {end - start > config.politenessDelay * (numTimes - 1)}

        // and that the httpClient was called
        verify(mockHttpClient, times(5)).execute(any())
    }
}