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
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueEntry
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueHSQLDao
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueIf
import io.thelandscape.krawler.crawler.KrawlQueue.ScheduledQueue
import io.thelandscape.krawler.http.*
import io.thelandscape.krawler.robots.RoboMinder
import io.thelandscape.krawler.robots.RoboMinderIf
import io.thelandscape.krawler.robots.RobotsConfig
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
                       private var krawlHistory: KrawlHistoryIf? = null,
                       internal var krawlQueues: List<KrawlQueueIf>? = null,
                       robotsConfig: RobotsConfig? = null,
                       private val requestProvider: RequestProviderIf = Requests(config),
                       private val threadpool: ThreadPoolExecutor = ThreadPoolExecutor(
                               config.numThreads,
                               config.numThreads,
                               config.emptyQueueWaitTime,
                               TimeUnit.SECONDS,
                               LinkedBlockingQueue<Runnable>(config.maximumQueueSize),
                               NoopTaskRejector())) {


    init {
        if (krawlHistory == null || krawlQueues == null) {
            val hsqlConnection: HSQLConnection = HSQLConnection(config.persistentCrawl, config.crawlDirectory)

            if (krawlHistory == null)
                krawlHistory = KrawlHistoryHSQLDao(hsqlConnection.hsqlSession)

            // This is safe because we don't have any KrawlHistoryIf implementations other than HSQL
            val histDao: KrawlHistoryHSQLDao = krawlHistory as KrawlHistoryHSQLDao

            if (krawlQueues == null)
                krawlQueues = (0 until config.numThreads).map {
                    KrawlQueueHSQLDao("queue$it", hsqlConnection.hsqlSession, histDao)
                }

        }
    }

    internal val scheduledQueue: ScheduledQueue = ScheduledQueue(krawlQueues!!, config)

    /**
     * Handle robots.txt
     */
    internal var minder: RoboMinderIf = RoboMinder(config.userAgent, requestProvider, robotsConfig ?: RobotsConfig())

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
    open protected fun onContentFetchError(url: KrawlUrl, reason: String) {
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

        (0 until krawlUrls.size).forEach {
            krawlQueues!![it % krawlQueues!!.size].push(listOf(KrawlQueueEntry(krawlUrls[it].canonicalForm)))
        }

        onCrawlStart()
        krawlQueues!!.forEach { threadpool.submit { doCrawl(it as KrawlQueueHSQLDao) } }
        while(!threadpool.isTerminated && threadpool.activeCount > 0) { Thread.sleep(250) }
        threadpool.shutdown()
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

        (0 until krawlQueues!!.size).forEach {
            krawlQueues!![it % krawlQueues!!.size].push(listOf(KrawlQueueEntry(krawlUrls[it].canonicalForm)))
        }

        onCrawlStart()
        krawlQueues!!.forEach { threadpool.submit { doCrawl(it as KrawlQueueHSQLDao) } }
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
            }
        }

    // Set of redirect codes
    private val redirectCodes: Set<Int> = setOf(300, 301, 302, 303, 307, 308)

    internal fun doCrawl(queue: KrawlQueueHSQLDao) {
        // If we're done, we're done
        if(!continueCrawling) return

        val entry: KrawlQueueEntry = scheduledQueue.pop() ?: return

        val krawlUrl: KrawlUrl = KrawlUrl.new(entry.url)
        val depth: Int = entry.depth
        val parent: KrawlUrl = KrawlUrl.new(entry.parent.url)

        // Make sure we're within depth limit
        if (depth >= config.maxDepth && config.maxDepth != -1)
            return

        val history: KrawlHistoryEntry =
                if (krawlHistory!!.hasBeenSeen(krawlUrl)) { // If it has been seen
                    onRepeatVisit(krawlUrl, parent)
                    return
                } else { // If it
                    krawlHistory!!.insert(krawlUrl)
                }

        // If we're supposed to visit this, get the HTML and call visit
        val visit = shouldVisit(krawlUrl)
        val check = shouldCheck(krawlUrl)
        if (visit || check) {

            // If we're respecting robots.txt check if it's ok to visit this page
            if (config.respectRobotsTxt && !minder.isSafeToVisit(krawlUrl))
                return

            visitCount++ // This will also set continueCrawling to false if the totalPages has been hit

            val doc: RequestResponse = requestProvider.getUrl(krawlUrl)

            // If there was an error on trying to get the doc, call content fetch error
            if (doc is ErrorResponse) {
                onContentFetchError(krawlUrl, doc.reason)
                return
            }

            // If there was an error parsing the response, still a content fetch error
            if (doc !is KrawlDocument) {
                onContentFetchError(krawlUrl, "Krawler was unable to parse the response from the server.")
                return
            }

            val links = harvestLinks(doc, krawlUrl, history, depth)

            queue.push(links)

            // Finally call visit
            if (visit)
                visit(krawlUrl, doc)

            if (check)
                check(krawlUrl, doc.statusCode)
        }
        // Re-schedule doCrawl
        threadpool.submit { doCrawl(queue) }
    }

    /**
     * Harvests all of the links from a KrawlQueueDocument and creates KrawlQueueEntries from them.
     * @param doc [KrawlDocument]: the document to harvest the anchor tags and other links from
     * @param url [KrawlUrl]: url that links are being harvested from
     * @param history [KrawlHistoryEntry]: the history entry generated for this URL
     * @param depth [Int]: The current crawl depth
     *
     * @return a list of [KrawlQueueEntry] containing the URLs to crawl
     */
    internal fun harvestLinks(doc: KrawlDocument, url: KrawlUrl,
                              history: KrawlHistoryEntry, depth: Int): List<KrawlQueueEntry> {

        // Handle redirects by getting the location tag of the header and pushing that into the queue
        if (!config.useFastRedirectStrategy && doc.statusCode in redirectCodes && config.followRedirects) {
            // Queue the redirected URL
            val locStr: String = doc.headers["location"] ?: return listOf()
            val location: KrawlUrl = KrawlUrl.new(locStr, url)
            // Decrement visit count since a redirect is sort of a continuation rather than a new page
            // TODO: Is this the behavior we want?
            visitCount--
            return listOf(KrawlQueueEntry(location.canonicalForm, history, depth))
        }

        // If it wasn't a redirect  parse out the URLs from anchor tags and construct queue entries from them
        return listOf(
                // Anchor tags
                doc.anchorTags
                        .filterNot { it.attr("href").startsWith("#") }
                        .map { KrawlUrl.new(it.attr("href"), url) }
                        .filter { it.canonicalForm.isNotBlank() }
                        .map { KrawlQueueEntry(it.canonicalForm, history, depth + 1) },
                // Everything else (img tags, scripts, etc)d
                doc.otherOutgoingLinks
                        .filterNot { it.startsWith("#")}
                        .map { KrawlUrl.new(it, url) }
                        .map { KrawlQueueEntry(it.canonicalForm, history, depth + 1)}
        ).flatten()
    }
}