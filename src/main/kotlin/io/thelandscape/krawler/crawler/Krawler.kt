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

package io.thelandscape.krawler.crawler

import io.thelandscape.krawler.HSQLConnection
import io.thelandscape.krawler.crawler.History.KrawlHistoryEntry
import io.thelandscape.krawler.crawler.History.KrawlHistoryHSQLDao
import io.thelandscape.krawler.crawler.History.KrawlHistoryIf
import io.thelandscape.krawler.http.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


/**
 * Class defines the operations and data structures used to perform a web crawl.
 *
 * @param config: A KrawlConfig object to control the limits and settings of the crawler
 * @param queue: A KrawlQueueIf provider, by default this will be a HSQL backed queue defined in the Dao
 *
 */
abstract class Krawler(val config: KrawlConfig = KrawlConfig(),
                       private val krawlHistory: KrawlHistoryIf =
                       KrawlHistoryHSQLDao(HSQLConnection(config.persistentKrawl, config.crawlDirectory).hsqlSession!!),
                       private val requestProvider: RequestProviderIf = Requests(config),
                       private val threadpool: ThreadPoolExecutor = ThreadPoolExecutor(config.numThreads,
                               config.numThreads,
                               config.emptyQueueWaitTime, TimeUnit.SECONDS,
                               LinkedBlockingQueue<Runnable>())) {

    init {
        // Set the core threadpool threads to destroy themselves after config.emptyQueueWaitTime
        threadpool.allowCoreThreadTimeOut(true)
    }

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
    open protected fun shouldCheck(url: KrawlUrl): Boolean = false

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
    open protected fun check(url: KrawlUrl, statusCode: Int) {
        return
    }

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
     * Function is called if a link to a previously visited page is scheduled to be crawled.
     * This can be overridden to take action when a URL has been seen multiple times.
     *
     * @param url KrawlUrl: URL of the page to visit
     * @param parent KrawlUrl: The URL of the parent page
     */
    open protected fun onRepeatVisit(url: KrawlUrl, parent: KrawlUrl) {
        return
    }

    /**
     * Function is called at the start of a crawl, prior to any worker threads taking any action.
     * This should be overridden to take an action prior to any worker threads running.
     */
    open protected fun onCrawlStart() {
        return
    }

    /**
     * Function is called at the end of a crawl, after all worker threads have exit.
     * This should be overridden to take an action after all worker threads have exit.
     */
    open protected fun onCrawlEnd() {
        return
    }

    /**
     * Arbitrary data structure associated with this crawl to provide context or a shared
     * resource.
     */
    open var crawlContext: Any? = null

    /**
     * Starts the Krawler with the URL provided. This method will call `onCrawlStart()`
     * perform the crawl and then call `onCrawlEnd()`. This method will block for the duration
     * of the crawl.
     *
     * @param: seedUrl String: A single seed URL
     */
    fun start(seedUrl: String) = start(listOf(seedUrl))

    /**
     * Starts the Krawler with the URLs provided. This method will call `onCrawlStart()`
     * perform the crawl and then call `onCrawlEnd()`. This method will block for the duration
     * of the crawl.
     *
     * @param: seedUrl List<String>: A list of seed URLs
     *
     */
    fun start(seedUrl: List<String>) {
        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map { KrawlUrl.new(it) }
        val entries: List<KrawlQueueEntry> = krawlUrls.map { KrawlQueueEntry(it.canonicalForm) }

        onCrawlStart()
        entries.forEach { threadpool.submit { doCrawl(it) } }
        while(!threadpool.isTerminated || threadpool.corePoolSize != 0) { Thread.sleep(500) }
        onCrawlEnd()
    }

    /**
     * Starts the Krawler with the URL provided. This method will call `onCrawlStart()`
     * start the crawl and then return. This method will -NOT- block during the crawl.
     * Note that because this method does not block, it will also not call `onCrawlEnd()`.
     *
     * @param: seedUrl String: A single seed URL
     */
    fun startNonblocking(seedUrl: String) = startNonblocking(listOf(seedUrl))

    /**
     * Starts the Krawler with the URLs provided. This method will call `onCrawlStart()`
     * start the crawl and then return. This method will -NOT- block during the crawl.
     * Note that because this method does not block, it will also not call `onCrawlEnd()`.
     *
     * @param: seedUrl List<String>: A list of seed URLs
     */
    fun startNonblocking(seedUrl: List<String>) {
        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map { KrawlUrl.new(it) }
        val entries: List<KrawlQueueEntry> = krawlUrls.map { KrawlQueueEntry(it.canonicalForm) }

        onCrawlStart()
        entries.forEach { threadpool.submit { doCrawl(it) } }
    }

    /**
     * Attempts to stop Krawler by gracefully shutting down. All current threads will finish
     * executing.
     */
    fun stop() = threadpool.shutdown()

    /**
     * Attempts to stop Krawler by forcing a shutdown. Threads may be killed mid-execution.
     */
    fun shutdown(): MutableList<Runnable> = threadpool.shutdownNow()

    /**
     * Private members
     */
    // Manage whether or not we should continue crawling
    private val continueLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private var continueCrawling: Boolean = true
        get() = continueLock.read { field }
        set(value) = continueLock.write { field = value }

    // Global visit count and domain visit count
    private val visitCountLock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    var visitCount: Int = 0
        get() = visitCountLock.read { field }
        private set(value) = visitCountLock.write {
            field = value

            // If we're setting the visit count to the configured number of
            // pages to crawl, flip the switch to stop crawling
            if (value == config.totalPages) {
                continueCrawling = false
                stop()
            }
        }

    internal fun doCrawl(entry: KrawlQueueEntry) {
        // If we're done, we're done
        if(!continueCrawling) return

        val krawlUrl: KrawlUrl = KrawlUrl.new(entry.url)
        val depth: Int = entry.depth

        val parent: KrawlUrl = KrawlUrl.new(entry.parent.url)

        // Make sure we're within depth limit
        val maxDepth: Int = config.maxDepth
        if (depth >= maxDepth && maxDepth != -1)
            return

        val history: KrawlHistoryEntry =
                if (krawlHistory.hasBeenSeen(krawlUrl)) { // If it has been seen
                    onRepeatVisit(krawlUrl, parent)
                    return
                } else { // If it
                    krawlHistory.insert(krawlUrl)
                }

        // If we're supposed to visit this, get the HTML and call visit
        if (shouldVisit(krawlUrl)) {
            visitCount += 1 // This will also set continueCrawling to false if the totalPages has been hit

            val doc: RequestResponse = requestProvider.getUrl(krawlUrl)

            // If there was an error on trying to get the doc, call content fetch error
            if (doc is ErrorResponse || doc !is KrawlDocument) {
                onContentFetchError(krawlUrl, ContentFetchError(krawlUrl, UnknownError()))
                return
            }

            // Parse out the URLs and construct queue entries from them
            val links: List<KrawlQueueEntry> = doc.anchorTags
                    .filterNot { it.attr("href").startsWith("#") }
                    .map { KrawlUrl.new(it, krawlUrl) }
                    .filterNotNull()
                    .filter { it.canonicalForm.isNotBlank() }
                    .map { KrawlQueueEntry(it.canonicalForm, history, depth + 1) }

            // Insert the URLs to the queue now
            links.forEach { threadpool.submit { doCrawl(it) } }

            // Finally call visit
            visit(krawlUrl, doc)
        }

        // If we're supposed to check this, get the status code and call check
        if (shouldCheck(krawlUrl)) {
            // Increment the check count
            visitCount += 1

            val doc: RequestResponse = requestProvider.checkUrl(krawlUrl)

            if (doc is ErrorResponse || doc !is KrawlDocument) {
                onContentFetchError(krawlUrl, ContentFetchError(krawlUrl, UnknownError()))
                return
            }

            val code: Int = doc.statusCode

            check(krawlUrl, code)
        }
    }
}
