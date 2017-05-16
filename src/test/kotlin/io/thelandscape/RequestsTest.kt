package io.thelandscape

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
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestTracker
import io.thelandscape.krawler.http.Requests
import kotlinx.coroutines.experimental.runBlocking
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RequestsTest {

    val mockHttpClient = mock<CloseableHttpClient> {}
    val config: KrawlConfig = KrawlConfig()
    val request: Requests = Requests(config, mockHttpClient)
    val testUrl = KrawlUrl.new("http://httpbin.org")
    val testUrl2 = KrawlUrl.new("http://nothttpbin.org/1/")

    @Test fun testRequestCheck() {
        runBlocking<Unit> {
            request.checkUrl(testUrl)
            // it should call execute once with an HttpHead
            // TODO: Swap the any() call to HttpHead somehow
        }
        verify(mockHttpClient, times(1)).execute(any<HttpUriRequest>(), any<HttpClientContext>())
    }

    @Test fun testRequestGet() = runBlocking<Unit> {
        val numTimes = 10
        val start = Instant.now().toEpochMilli()
        (1 .. numTimes).forEach {
            request.getUrl(testUrl)
            // Issue two requests
            request.getUrl(testUrl2)
        }
        val end = Instant.now().toEpochMilli()

        // Make sure that the politeness delay is respected
        // This delay should take politeness delay * (n - 1) requests (first doesn't wait)
        assertTrue {end - start > config.politenessDelay * (numTimes - 1)}

        // and that the httpClient was called
        verify(mockHttpClient, times(numTimes * 2)).execute(any<HttpUriRequest>(), any<HttpClientContext>())
    }
}

class RequestTrackerTest {

    val requestTracker: RequestTracker = RequestTracker()

    @Test fun testGetLock() {
        val first = runBlocking { requestTracker.getLock("test") }
        val second = runBlocking { requestTracker.getLock("test") }
        val third = runBlocking { requestTracker.getLock("test2") }

        // Test that the same parameter actually gets the same lock
        assertEquals(first, second)
        // Test that a different parameter gets a different lock
        assertNotEquals(first, third)
    }

    @Test fun testGetAndSetTimestamp() {
        // First test that the inital timestamp is zero
        val first = requestTracker.getTimestamp("test")
        assertEquals(0, first)

        // Next test that setting the timestamp works properly
        val now = Instant.now().toEpochMilli()
        requestTracker.setTimestamp("test", now)

        val second = requestTracker.getTimestamp("test")
        assertEquals(now, second)
    }
}
