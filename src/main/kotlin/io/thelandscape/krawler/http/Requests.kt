/**
 * Created by @brianmadden on 10/21/16.
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

package io.thelandscape.krawler.http

import io.thelandscape.krawler.crawler.KrawlConfig
import org.apache.http.HttpResponse
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

interface RequestProviderIf {
    /**
     * Method to check the status code of a URL
     */
    fun checkUrl(url: KrawlUrl): RequestResponse

    /**
     * Method to get the contents of a URL
     */
    fun getUrl(url: KrawlUrl): RequestResponse
}

private val pcm: PoolingHttpClientConnectionManager = PoolingHttpClientConnectionManager()
private val requestConfig = RequestConfig.custom()
        .setCookieSpec(CookieSpecs.STANDARD)
        .setExpectContinueEnabled(false)
        .setContentCompressionEnabled(true)
        .setRedirectsEnabled(true)
        .build()

// TODO: Clean up the connection pool somewhere

class Requests(val krawlConfig: KrawlConfig,
               val httpClient: CloseableHttpClient =
               HttpClients.custom()
                       .setDefaultRequestConfig(requestConfig)
                       .setConnectionManager(pcm).build()) : RequestProviderIf {

    /** Check a URL and return it's status code
     * @param url KrawlUrl: the url to check
     *
     * @return RequestResponse: KrawlDocument containing the status code, or ErrorResponse on error
     */
   override fun checkUrl(url: KrawlUrl): RequestResponse = makeRequest(url, ::HttpHead, ::KrawlDocument)

    /** Get the contents of a URL
     * @param url KrawlUrl: the URL to get the contents of
     *
     * @return KrawlDocument: The parsed HttpResponse returned by the GET request
     */
    override fun getUrl(url: KrawlUrl): RequestResponse = makeRequest(url, ::HttpGet, ::KrawlDocument)

    // Hash map to track requests and respect politeness
    // TODO: Make politeness delay a per-domain or per-host thing, or is the politeness for ourselves? ;)
    private var lastRequest: Long = 0
    private val requestMutex = Any()

    /** Convenience function for building, & issuing the HttpRequest
     * @param url KrawlUrl: Url to make request to
     * @param reqFun: Function used to construct the request
     * @param retFun: Function used to construct the response object
     */
    private fun makeRequest(url: KrawlUrl,
                            reqFun: (String) -> HttpUriRequest,
                            retFun: (KrawlUrl, HttpResponse) -> RequestResponse): RequestResponse {

        val req: HttpUriRequest = reqFun(url.canonicalForm)

        // Handle politeness
        if (krawlConfig.politenessDelay > 0) {
            synchronized(requestMutex) {
                val reqDelta = Instant.now().toEpochMilli() - lastRequest
                if (reqDelta >= 0 && reqDelta < krawlConfig.politenessDelay)
                // Sleep until the remainder of the politeness delay has elapsed
                    Thread.sleep(krawlConfig.politenessDelay - reqDelta)
                // Set last request time for politeness
                lastRequest = Instant.now().toEpochMilli()
            }
        }

        val resp: RequestResponse = try {
            val response: HttpResponse? = httpClient.execute(req)
            if (response == null) ErrorResponse(url) else retFun(url, response)
        } catch (e: Exception) {
            throw ContentFetchError(url, e)
        }

        return resp
    }
}

/**
 * Error representing any failure to fetch content.
 * Will contain the root cause exception as well as the requested URL.
 */
class ContentFetchError(url: KrawlUrl, cause: Throwable):
        Throwable("Failed to retrieve the content for ${url.canonicalForm}.", cause)