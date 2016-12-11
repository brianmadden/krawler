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
import io.thelandscape.krawler.http.ContentFetchError
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.Requests
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Test

private val mockHttpClient = mock<CloseableHttpClient> {}

class RequestsTest {

    val request: Requests = Requests(mockHttpClient)
    val testUrl = KrawlUrl.new("http://httpbin.org")

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
        try {
            request.getUrl(testUrl)
        } catch (e: ContentFetchError) {
            // Ignore this, it's expected
        }
        verify(mockHttpClient, times(2)).execute(any())
    }

    @Test fun testFailedRequest() {
      TODO()
    }

}