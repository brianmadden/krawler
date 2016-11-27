package io.thelandscape.krawler.crawler

import io.thelandscape.krawler.crawler.Frontier.krawlFrontier
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueDao
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueIf
import io.thelandscape.krawler.crawler.KrawlQueue.QueueEntry
import io.thelandscape.krawler.http.ContentFetchError
import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

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

/**
 * Class defines the operations and data structures used to perform a web crawl.
 *
 * @param config: A KrawlConfig object to control the limits and settings of the crawler
 * @param queue: A KrawlQueueIf provider, by default this will be a HSQL backed queue defined in the Dao
 *
 */
abstract class Krawler(val config: KrawlConfig = KrawlConfig(),
                       private val queue: KrawlQueueIf = KrawlQueueDao,
                       private val threadpool: ExecutorService = Executors.newFixedThreadPool(config.numThreads)) {

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
     * Note: This will not GET the content of a page, and as a result will not follow any links on the checked page.
     * As such checking a page should be reserved for content that does not contain hyperlinks.
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
     * Note: This will not GET the content of a page, and as a result will not follow any links on the checked page.
     * As such checking a page should be reserved for content that does not contain hyperlinks.
     * @param url KrawlURL: The requested URL
     * @param statusCode Int: The resulting status code from checking the URL
     */
    abstract protected fun check(url: KrawlUrl, statusCode: Int)

    /**
     * Function is called on unexpected status code (non 200).
     * This can be overridden to take action on other status codes (500, 404, etc)
     *
     * @param url KrawlUrl: The URL that failed
     * @param statusCode Int: The status code
     */
    open protected fun onUnexpectedStatusCode(url: KrawlUrl, statusCode: Int) {
        return
    }

    /**
     * Function is called on content fetch error.
     * This can be overridden to take action on content fetch errors.
     *
     * @param url KrawlUrl: The URL that failed
     * @param error ContentFetchError: The content fetch error that was thrown.
     */
    open protected fun onContentFetchError(url: KrawlUrl, error: ContentFetchError) {
        return
    }

    /**
     * Function is called if a link to a previously visited page is found.
     * This can be overridden to take action when a URL has been seen multiple times.
     *
     * @param url KrawlUrl: URL of the source page
     */
    open protected fun onDuplicateVisit(url: KrawlUrl) {
        return
    }

    fun startNonblocking(): Unit = TODO()

    fun start(seedUrl: String) = start(listOf(seedUrl))

    fun start(seedUrl: List<String>) {
        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map { KrawlUrl.new(it) }

        // Insert the seeds
        queue.push(krawlUrls.map{ QueueEntry(it.canonicalForm, 0) })

        (0..config.numThreads - 1).forEach { threadpool.submit { doCrawl() } }
    }

    fun stop() = threadpool.shutdown()

    fun shutdown() = threadpool.shutdownNow()

    /**
     * Private members
     */
    // Manage whether or not we should continue crawling
    private val continueLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private var continueCrawling: Boolean = true
        get() = continueLock.read { field }
        set(value) = continueLock.write { field = value }

    // Global visit count and domain visit count
    private val globalVisitCountLock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    var globalVisitCount: Int = 0
        get() = globalVisitCountLock.read { field }
        private set(value) = globalVisitCountLock.write { field = value }

    val domainVisitCounts: MutableMap<String, Int> = ConcurrentHashMap()

    private fun doCrawl() {

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

            val krawlUrl: KrawlUrl = KrawlUrl.new(qe!!.url)
            val depth: Int = qe.depth

            // Make sure we're within depth limit
            if (depth >= config.maxDepth)
                break

            // Make sure we're within domain limits
            // TODO: Fix race condition where you can crawl config.numThreads extra pages per domain
            val domainCount = domainVisitCounts.getOrElse(krawlUrl.domain, { 0 })
            if (domainCount >= config.domainTotalPages)
                break

            // If it's a duplicate keep moving
            if (krawlFrontier.verifyUnique(krawlUrl)) {
                onDuplicateVisit(krawlUrl)
                continue
            }

            // Increment the domain visit count
            domainVisitCounts[krawlUrl.domain] = domainCount + 1

            // If we're supposed to visit this, get the HTML and call visit
            if (shouldVisit(krawlUrl)) {
                val doc: KrawlDocument = Request.getUrl(krawlUrl)

                // Parse out the URLs and construct queue entries from them
                val links: List<QueueEntry> = doc.anchorTags
                        .map { KrawlUrl.new(it) }
                        .filterNotNull()
                        .map { QueueEntry(it.canonicalForm, depth + 1) }

                // Insert the URLs to the queue now
                queue.push(links)

                // Finally call visit
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