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
import io.thelandscape.krawler.robots.RobotsTxt
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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface RequestProviderIf {
    /**
     * Method to check the status code of a URL
     */
    fun checkUrl(url: KrawlUrl): RequestResponse

    /**
     * Method to get the contents of a URL
     */
    fun getUrl(url: KrawlUrl): RequestResponse

    /**
     * Method to get a robots.txt from a KrawlUrl
     */
    fun fetchRobotsTxt(url: KrawlUrl): RequestResponse
}

private val pcm: PoolingHttpClientConnectionManager = PoolingHttpClientConnectionManager()

class Requests(private val krawlConfig: KrawlConfig,
               private var httpClient: CloseableHttpClient? = null) : RequestProviderIf {

    init {
        if (httpClient == null) {
            val requestConfig = RequestConfig.custom()
                    .setCookieSpec(CookieSpecs.STANDARD)
                    .setExpectContinueEnabled(false)
                    .setContentCompressionEnabled(krawlConfig.allowContentCompression)
                    .setRedirectsEnabled(krawlConfig.followRedirects)
                    .setConnectionRequestTimeout(krawlConfig.connectionRequestTimeout)
                    .setConnectTimeout(krawlConfig.connectTimeout)
                    .setSocketTimeout(krawlConfig.socketTimeout)
                    .build()

            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setUserAgent(krawlConfig.userAgent)
                    .setConnectionManager(pcm).build()
        }
    }

    /** Fetch the robots.txt file from a domain
     * @param url [KrawlUrl]: The URL to fetch robots from
     *
     * @return [RequestResponse]: The parsed robots.txt or, or ErrorResponse on error
     */
    override fun fetchRobotsTxt(url: KrawlUrl): RequestResponse {
        val robotsRequest = KrawlUrl.new("${url.hierarchicalPart}/robots.txt")
        return makeRequest(robotsRequest, ::HttpGet, ::RobotsTxt)
    }

    /** Check a URL and return it's status code
     * @param url KrawlUrl: the url to check
     *
     * @return [RequestResponse]: KrawlDocument containing the status code, or ErrorResponse on error
     */
   override fun checkUrl(url: KrawlUrl): RequestResponse = makeRequest(url, ::HttpHead, ::KrawlDocument)

    /** Get the contents of a URL
     * @param url KrawlUrl: the URL to get the contents of
     *
     * @return [RequestResponse]: The parsed HttpResponse returned by the GET request
     */
    override fun getUrl(url: KrawlUrl): RequestResponse = makeRequest(url, ::HttpGet, ::KrawlDocument)

    // Hash map to track requests and respect politeness
    private val requestTracker: RequestTracker = RequestTracker()

    /** Convenience function for building, & issuing the HttpRequest
     * @param url KrawlUrl: Url to make request to
     * @param reqFun: Function used to construct the request
     * @param retFun: Function used to construct the response object
     */
    private fun makeRequest(url: KrawlUrl,
                            reqFun: (String) -> HttpUriRequest,
                            retFun: (KrawlUrl, HttpResponse) -> RequestResponse): RequestResponse {

        val req: HttpUriRequest = reqFun(url.canonicalForm)
        val host: String = url.host

        // Handle politeness

        if (krawlConfig.politenessDelay > 0) {
            synchronized(requestTracker.getLock(host)) {
                val reqDelta = Instant.now().toEpochMilli() - requestTracker.getTimestamp(host)
                if (reqDelta >= 0 && reqDelta < krawlConfig.politenessDelay)
                // Sleep until the remainder of the politeness delay has elapsed
                    Thread.sleep(krawlConfig.politenessDelay - reqDelta)
                // Set last request time for politeness
                requestTracker.setTimestamp(host, Instant.now().toEpochMilli())
            }
        }

        val resp: RequestResponse = try {
            val response: HttpResponse? = httpClient!!.execute(req)
            if (response == null) ErrorResponse(url) else retFun(url, response)
        } catch (e: Exception) {
            ErrorResponse(url, e.toString() ?: "An unknown error has occurred.")
        }

        return resp
    }
}

/**
 * Class to track requests timestamps, and the locks that will be used to synchronize the timestamp
 * access.
 */
class RequestTracker {

    private val lockMapLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private val lockMap: MutableMap<String, Any> = mutableMapOf()
    private val timestampMap: MutableMap<String, Long> = mutableMapOf()

    /**
     * Gets the lock associated with a specific host that will be used to synchronize the politeness
     * delay code section.
     *
     * Note: This operation -IS- threadsafe.
     *
     * @param host String: The host that this lock will be associated with.
     * @return the [Any] object that will lock the synchronized section
     */
    fun getLock(host: String): Any {
        return lockMapLock.read { lockMap[host] } ?:
                lockMapLock.write { lockMap.getOrPut(host, { Any() }) }
    }

    /**
     * Gets the timestamp associated with a specific host, to determine when the next request is safe
     * to send.
     *
     * Note: This operation is -NOT- threadsafe and should only be used in a synchronized block.
     *
     * @param host String: The host associated with this timestamp
     * @return Long representing the timestamp in milliseconds since the epoch
     */
    fun getTimestamp(host: String): Long {
        return timestampMap.getOrPut(host, { 0 })
    }

    /**
     * Sets the timestamp associated with a specific host.
     *
     * Note: This operation is -NOT- threadsafe and should only be used in a synchronized block.
     *
     * @param host String: The host associated with this timestamp
     * @param value Long: The timestamp in milliseconds since the epoch
     * @return [Unit]
     */
    fun setTimestamp(host: String, value: Long) = timestampMap.put(host, value)
}
