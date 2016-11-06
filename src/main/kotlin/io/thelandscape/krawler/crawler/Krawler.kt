package io.thelandscape.krawler.crawler

import com.sun.org.apache.xpath.internal.operations.Bool
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueue
import io.thelandscape.krawler.crawler.KrawlQueue.QueueEntry
import io.thelandscape.krawler.http.ContentFetchError
import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.Request

/**
 * Created by brian.a.madden@gmail.com on 10/26/16.
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

abstract class Krawler(val config: KrawlConfig) {

    /**
     * Override this function to determine if a URL should be visited.
     * Visiting a URL will issue an HTTP GET request.
     * @param url KrawlUrl: The URL to consider visiting.
     *
     * @return boolean: true if we should visit, false otherwise
     */
    abstract protected fun shouldVisit(url: KrawlUrl): Boolean

    /**
     * Override this function to determine if a URL should be checked.
     * Checking a URL will issue an HTTP HEAD request, and return only a status code.
     * @param url KrawlUrl: The URL to consider visiting.
     *
     * @return boolean: true if we should check, false otherwise
     */
    abstract protected fun shouldCheck(url: KrawlUrl): Boolean

    /**
     * Visit a URL by issuing an HTTP GET request
     * @param url KrawlURL: The requested URL
     * @param doc KrawlDocument: The resulting document from visting the URL
     */
    abstract protected fun visit(url: KrawlUrl, doc: KrawlDocument)

    /**
     * Check a URL by issuing an HTTP HEAD request
     * @param url KrawlURL: The requested URL
     * @param statusCode Int: The resulting status code from checking the URL
     */
    abstract protected fun check(url: KrawlUrl, statusCode: Int)

    /**
     * Function is called on unexpected status code (non 200).
     * This can be overridden to take action on other status codes (500, 404, etc)
     */
    protected fun onUnexpectedStatusCode(statusCode: Int) {
        return
    }

    /**
     * Function is called on content fetch error.
     * This can be overridden to take action on content fetch errors.
     */
    protected fun onContentFetchError(error: ContentFetchError) {
        return
    }


    fun start(seedUrl: String) = start(listOf(seedUrl))

    fun start(seedUrl: List<String>) = doCrawl(seedUrl)

    fun stop() { TODO() }

    fun shutdown() { TODO() }

    private fun doCrawl(seedUrl: List<String>) {

        // Create our queue
        val queue: KrawlQueue = KrawlQueue()

        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map(::KrawlUrl)

        // Insert the seeds
        queue.push(krawlUrls.map{ QueueEntry(it.canonicalForm, 0) })

        // State variables
        var continueCrawling: Boolean = true
        val domainVisitCounts: Map<String, Int> = mutableMapOf()
        var globalVisitCount: Int = 0
        var emptyQueueWaitCount: Int = 0

        while(continueCrawling) {

            // Quit if global threshold met
            if (globalVisitCount == config.globalTotalPages)
                break

            // Pop a URL off the queue
            var qe: QueueEntry? = queue.pop()
            if (qe == null) {

                // Wait for the configured period for more URLs
                while(emptyQueueWaitCount < config.emptyQueueWait) {
                    Thread.sleep(1000)
                    emptyQueueWaitCount++

                    // Try to pop again
                    qe = queue.pop()

                    // If we have something, reset the count and move on
                    if (qe != null) {
                        emptyQueueWaitCount = 0
                        break
                    }

                    // If we've hit the limit, time to quit
                    if (emptyQueueWaitCount == config.emptyQueueWait)
                        return
                }
            }

            val krawlUrl: KrawlUrl = KrawlUrl(qe!!.url)

            // Make sure we're within domain limits
            if (domainVisitCounts.getOrElse(krawlUrl.domain, {0}) >= config.domainTotalPages)
                break

            // If we're supposed to visit this, get the HTML and call visit
            if (shouldVisit(krawlUrl)) {
                val doc: KrawlDocument = Request.getUrl(krawlUrl)
                visit(krawlUrl, doc)
            }

            // If we're supposed to check this, get the status code and call check
            if (shouldCheck(krawlUrl)) {
                val code: Int = Request.checkUrl(krawlUrl)
                check(krawlUrl, code)
            }
        }
    }

}