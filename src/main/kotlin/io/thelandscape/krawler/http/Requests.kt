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
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.sync.Mutex
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContextBuilder
import java.security.cert.X509Certificate
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

internal class HistoryTrackingRedirectStrategy: DefaultRedirectStrategy() {
    override fun getRedirect(request: HttpRequest, response: HttpResponse,
                             context: HttpContext): HttpUriRequest {

        val newRequest = super.getRedirect(request, response, context)

        val node: RedirectHistoryNode = RedirectHistoryNode(request.requestLine.uri, response.statusLine.statusCode,
                newRequest.uri.toASCIIString())

        val redirectHistory: List<RedirectHistoryNode> =
                context.getAttribute("fullRedirectHistory") as List<RedirectHistoryNode>? ?: listOf<RedirectHistoryNode>()
        context.setAttribute("fullRedirectHistory", redirectHistory + listOf(node))

        return newRequest
    }
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
                    .setRedirectsEnabled(krawlConfig.followRedirects && krawlConfig.useFastRedirectStrategy)
                    .setConnectionRequestTimeout(krawlConfig.connectionRequestTimeout)
                    .setConnectTimeout(krawlConfig.connectTimeout)
                    .setSocketTimeout(krawlConfig.socketTimeout)
                    .build()

            val trustStrat = TrustStrategy { arrayOfX509Certificates: Array<X509Certificate>, s: String -> true }

            val sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, trustStrat)
                    .build()

            val redirectStrategy = HistoryTrackingRedirectStrategy()

            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier())
                    .setRedirectStrategy(redirectStrategy)
                    .setUserAgent(krawlConfig.userAgent)
                    .setConnectionManager(pcm)
                    .build()
        }
    }

    /** Fetch the robots.txt file from a domain
     * @param url [KrawlUrl]: The URL to fetch robots from
     *
     * @return [RequestResponse]: The parsed robots.txt or, or ErrorResponse on error
     */
    override fun fetchRobotsTxt(url: KrawlUrl): RequestResponse {
        val robotsRequest = KrawlUrl.new("${url.hierarchicalPart}/robots.txt")
        return runBlocking(CommonPool) { makeRequest(robotsRequest, ::HttpGet, ::RobotsTxt) }
    }

    /** Check a URL and return it's status code
     * @param url KrawlUrl: the url to check
     *
     * @return [RequestResponse]: KrawlDocument containing the status code, or ErrorResponse on error
     */
   override fun checkUrl(url: KrawlUrl): RequestResponse = runBlocking(CommonPool) {
        makeRequest(url, ::HttpHead, ::KrawlDocument)
    }

    /** Get the contents of a URL
     * @param url KrawlUrl: the URL to get the contents of
     *
     * @return [RequestResponse]: The parsed HttpResponse returned by the GET request
     */
    override fun getUrl(url: KrawlUrl): RequestResponse = runBlocking(CommonPool) {
        makeRequest(url, ::HttpGet, ::KrawlDocument)
    }

    // Hash map to track requests and respect politeness
    private val requestTracker: RequestTracker = RequestTracker()

    /** Convenience function for building, & issuing the HttpRequest
     * @param url KrawlUrl: Url to make request to
     * @param reqFun: Function used to construct the request
     * @param retFun: Function used to construct the response object
     */
    private suspend fun makeRequest(url: KrawlUrl,
                                    reqFun: (String) -> HttpUriRequest,
                                    retFun: (KrawlUrl, HttpResponse, HttpClientContext) -> RequestResponse): RequestResponse {

        val httpContext = HttpClientContext()
        httpContext.setAttribute("fullRedirectHistory", listOf<RedirectHistoryNode>())

        val req: HttpUriRequest = reqFun(url.canonicalForm)
        val host: String = url.host

        // Handle politeness

        if (krawlConfig.politenessDelay > 0) {
            val myLock = requestTracker.getLock(host)
            myLock.lock()
            try {
                val reqDelta = Instant.now().toEpochMilli() - requestTracker.getTimestamp(host)
                if (reqDelta >= 0 && reqDelta < krawlConfig.politenessDelay)
                // Sleep until the remainder of the politeness delay has elapsed
                    Thread.sleep(krawlConfig.politenessDelay - reqDelta)
                // Set last request time for politeness
                requestTracker.setTimestamp(host, Instant.now().toEpochMilli())
            } finally {
                myLock.unlock()
            }
        }

        val resp: RequestResponse = try {
            val response: HttpResponse? = httpClient!!.execute(req, httpContext)
            if (response == null) ErrorResponse(url) else retFun(url, response, httpContext)
        } catch (e: Throwable) {
            ErrorResponse(url, e.toString())
        }

        return resp
    }
}

/**
 * Class to track requests timestamps, and the locks that will be used to synchronize the timestamp
 * access.
 */
class RequestTracker {

    private val lockMapLock: Mutex = Mutex()
    private val lockMap: MutableMap<String, Mutex> = mutableMapOf()
    private val timestampMap: MutableMap<String, Long> = mutableMapOf()

    /**
     * Gets the lock associated with a specific host that will be used to synchronize the politeness
     * delay code section.
     *
     * Note: This operation -IS- threadsafe.
     *
     * @param host String: The host that this lock will be associated with.
     * @return the [Mutex] object that will lock the synchronized section
     */
    suspend fun getLock(host: String): Mutex {
        lockMapLock.lock()
        return try { lockMap[host] ?: lockMap.getOrPut(host, { Mutex() }) } finally {lockMapLock.unlock() }
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
