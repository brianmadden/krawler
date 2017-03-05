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
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.sync.Mutex
import java.util.concurrent.CancellationException

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

    private val logger: Logger = LogManager.getLogger()

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
        logger.debug("Krawler initialized with ${config.numThreads} threads.")
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
    suspend fun start(seedUrl: String) = start(listOf(seedUrl))

    /**
     * Starts the Krawler with the URLs provided. This method will call `onCrawlStart()`
     * perform the crawl and then call `onCrawlEnd()`. This method will block for the duration
     * of the crawl.
     *
     * @param: seedUrl List<String>: A list of seed URLs
     *
     */
    suspend fun start(seedUrl: List<String>) = runBlocking(CommonPool) {
        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map { KrawlUrl.new(it) }

        krawlUrls.forEach {
            scheduledQueue.push(it.domain, listOf(KrawlQueueEntry(it.canonicalForm)))
        }

        onCrawlStart()
        (1..krawlUrls.size).forEach { async(CommonPool + job) { doCrawl() } }
        while (job.isActive) { delay(250) }
        onCrawlEnd()
    }

    /**
     * Starts the Krawler with the URL provided. This method will call `onCrawlStart()`
     * start the crawl and then return. This method will -NOT- block during the crawl.
     * Note that because this method does not block, it will also not call `onCrawlEnd()`.
     *
     * @param: seedUrl String: A single seed URL
     */
    suspend fun startNonblocking(seedUrl: String) = startNonblocking(listOf(seedUrl))

    /**
     * Starts the Krawler with the URLs provided. This method will call `onCrawlStart()`
     * start the crawl and then return. This method will -NOT- block during the crawl.
     * Note that because this method does not block, it will also not call `onCrawlEnd()`.
     *
     * @param: seedUrl List<String>: A list of seed URLs
     */
    suspend fun startNonblocking(seedUrl: List<String>) {
        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map { KrawlUrl.new(it) }

        krawlUrls.forEach {
            scheduledQueue.push(it.domain, listOf(KrawlQueueEntry(it.canonicalForm)))
        }

        onCrawlStart()
        (1..krawlUrls.size).forEach { launch(CommonPool + job) { doCrawl() } }
    }


    /**
     * Returns true if the threadpool is still actively running tasks, false otherwise
     *
     * @return: true if threadpool is active, false otherwise
     */
    fun isActive(): Boolean {
        return threadpool.activeCount > 0
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
    // Lock for the synchronized block to determine when to stop
    private val syncMutex: Mutex = Mutex()

    /** This should be utilized within a locked or synchronized block **/
    var visitCount: Int = 0
        private set

    // Set of redirect codes
    private val redirectCodes: Set<Int> = setOf(300, 301, 302, 303, 307, 308)

    private val job: Job = Job()

    internal suspend fun doCrawl() {
        val entry: KrawlQueueEntry = scheduledQueue.pop() ?: return

        val krawlUrl: KrawlUrl = KrawlUrl.new(entry.url)
        val depth: Int = entry.depth
        val parent: KrawlUrl = KrawlUrl.new(entry.parent.url)

        // Make sure we're within depth limit
        if (depth >= config.maxDepth && config.maxDepth != -1) {
            return
        }

        val history: KrawlHistoryEntry =
                if (krawlHistory!!.hasBeenSeen(krawlUrl)) { // If it has been seen
                    onRepeatVisit(krawlUrl, parent)
                    return
                } else {
                    krawlHistory!!.insert(krawlUrl)
                }

        // If we're supposed to visit this, get the HTML and call visit
        val visit = shouldVisit(krawlUrl)
        val check = shouldCheck(krawlUrl)

        if (visit || check) {
            // If we're respecting robots.txt check if it's ok to visit this page
            if (config.respectRobotsTxt && !minder.isSafeToVisit(krawlUrl)) {
                return
            }
            // Check if we should continue crawling
            syncMutex.lock()
            try {
                if ((++visitCount > config.totalPages) && (config.totalPages > -1)) {
                    job.cancel()
                    return
                }
            } catch (e: CancellationException) {
                logger.info("Hit number of pages, cancelling jobs...")
                // Do nothing, this is okay
            } finally {
                syncMutex.unlock()
            }

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
            scheduledQueue.push(krawlUrl.domain, links)

            (0 until links.size).forEach {
                launch(CommonPool + job) {
                    doCrawl()
                }
            }

            // Finally call visit
            if (visit)
                visit(krawlUrl, doc)

            if (check)
                check(krawlUrl, doc.statusCode)
        }
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
    internal suspend fun harvestLinks(doc: KrawlDocument, url: KrawlUrl,
                              history: KrawlHistoryEntry, depth: Int): List<KrawlQueueEntry> {

        // Handle redirects by getting the location tag of the header and pushing that into the queue
        if (!config.useFastRedirectStrategy && doc.statusCode in redirectCodes && config.followRedirects) {
            // Queue the redirected URL
            val locStr: String = doc.headers["location"] ?: return listOf()
            val location: KrawlUrl = KrawlUrl.new(locStr, url)

            // We won't count it as a visit sinc
            syncMutex.lock()
            try { visitCount-- }
            finally { syncMutex.unlock() }

            return listOf(KrawlQueueEntry(location.canonicalForm, history, depth))
        }

        // If it wasn't a redirect  parse out the URLs from anchor tags and construct queue entries from them
        return listOf(
                // Anchor tags
                doc.anchorTags
                        .filterNot { it.attr("href").startsWith("#") }
                        .filter { it.attr("href").length <= 2048 }
                        .map { KrawlUrl.new(it.attr("href"), url) }
                        .filter { it.canonicalForm.isNotBlank() }
                        .map { KrawlQueueEntry(it.canonicalForm, history, depth + 1) },
                // Everything else (img tags, scripts, etc)d
                doc.otherOutgoingLinks
                        .filterNot { it.startsWith("#")}
                        .filter { it.length <= 2048 }
                        .map { KrawlUrl.new(it, url) }
                        .map { KrawlQueueEntry(it.canonicalForm, history, depth + 1)}
        ).flatten()
    }
}