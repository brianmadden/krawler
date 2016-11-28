package io.thelandscape.krawler.http

import io.thelandscape.krawler.crawler.History.KrawlHistoryEntry
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

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

val Request: Requests = Requests()

class Requests(val httpClient: CloseableHttpClient = HttpClients.createDefault()) {

    /** Check a URL and return it's status code
     * @param url KrawlUrl: the url to check
     *
     * @return Int: the status code returned by the HttpHead request
     */
    fun checkUrl(url: KrawlUrl): Int = requestAndClose(url, ::HttpHead, ::KrawlDocument).statusCode

    /** Get the contents of a URL
     * @param url KrawlUrl: the URL to get the contents of
     *
     * @return KrawlDocument: The parsed HttpResponse returned by the GET request
     */
    fun getUrl(url: KrawlUrl): KrawlDocument = requestAndClose(url, ::HttpGet, ::KrawlDocument)

    /** Convenience function for building, issuing, and closing the HttpRequest
     * @param url KrawlUrl: Url to make request to
     * @param reqFun: Function used to construct the request
     * @param retFun: Function used to construct the response object
     */
    private fun <T> requestAndClose(url: KrawlUrl,
                                    reqFun: (String) -> HttpUriRequest,
                                    retFun: (HttpResponse) -> T): T {

        val req: HttpUriRequest = reqFun(url.canonicalForm)
        val resp: T = try {
            val response: HttpResponse = httpClient.execute(req)
            retFun(response)
        } catch (e: Exception) {
            throw ContentFetchError(url, e)
        } finally {
            httpClient.close()
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