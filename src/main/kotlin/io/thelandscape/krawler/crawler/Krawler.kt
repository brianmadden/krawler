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
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class defines the operations and data structures used to perform a web crawl.
 *
 * @param config: A KrawlConfig object to control the limits and settings of the crawler
 * @param krawlHistory: KrawlHistoryIf provider, by default this will be a HSQL backed table
 * @param krawlQueues: A KrawlQueueIf provider, by default this will be a HSQL backed queue
 * @param robotsConfig: Configuration of the robots.txt management
 * @param requestProvider: RequestProviderIf provider, default is Requests class
 * @param job: Job context that threads will run in.
 *
 */
abstract class Krawler(val config: KrawlConfig = KrawlConfig(),
                       private var krawlHistory: KrawlHistoryIf? = null,
                       private var krawlQueues: List<KrawlQueueIf>? = null,
                       robotsConfig: RobotsConfig? = null,
                       private val requestProvider: RequestProviderIf = Requests(config),
                       private val job: Job = Job()) {

    private val logger: Logger = LogManager.getLogger()

    // Map of start URL -> int id to track branches of a crawl
    protected val rootPageIds: Map<String, Int>
        get() = _rootPageIds.toMap()

    // Map of start URL -> int id to track branches of a crawl
    private val _rootPageIds: MutableMap<String, Int> = ConcurrentHashMap()
    // Current Max root page ID
    private val maximumUsedId: AtomicInteger = AtomicInteger(0)

    init {
        if (krawlHistory == null || krawlQueues == null) {
            val hsqlConnection = HSQLConnection(config.persistentCrawl, config.crawlDirectory)

            if (krawlHistory == null)
                krawlHistory = KrawlHistoryHSQLDao(hsqlConnection.hsqlSession)

            // This is safe because we don't have any KrawlHistoryIf implementations other than HSQL
            val histDao: KrawlHistoryHSQLDao = krawlHistory as KrawlHistoryHSQLDao

            if (krawlQueues == null)
                // TODO: Dynamic number of queues? Why 10?
                krawlQueues = (0 until 10).map {
                    KrawlQueueHSQLDao("queue$it", hsqlConnection.hsqlSession, histDao)
                }

        }

        job.invokeOnCompletion {
            logger.debug("Ending here... (job is no longer active)!!!!")
            onCrawlEnd()
        }
    }

    private val scheduledQueue: ScheduledQueue = ScheduledQueue(krawlQueues!!, config, job)

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
    abstract protected fun shouldVisit(url: KrawlUrl, queueEntry: KrawlQueueEntry): Boolean

    /**
     * Override this function to determine if a URL should be checked.
     * Checking a URL will issue an HTTP HEAD request, and return only a status code.
     * Note: This will not GET the content of a page, and as a result will not follow any links on the checked page.
     * As such checking a page should be reserved for content that does not contain hyperlinks.
     * @param url KrawlUrl: The URL to consider visiting.
     *
     * @return boolean: true if we should check, false otherwise
     */
    open protected fun shouldCheck(url: KrawlUrl, queueEntry: KrawlQueueEntry): Boolean = false

    /**
     * Visit a URL by issuing an HTTP GET request
     * @param url KrawlURL: The requested URL
     * @param doc KrawlDocument: The resulting document from visting the URL
     */
    abstract protected fun visit(url: KrawlUrl, doc: KrawlDocument, queueEntry: KrawlQueueEntry)

    /**
     * Check a URL by issuing an HTTP HEAD request
     * Note: This will not GET the content of a page, and as a result will not follow any links on the checked page.
     * As such checking a page should be reserved for content that does not contain hyperlinks.
     * @param url KrawlURL: The requested URL
     * @param statusCode Int: The resulting status code from checking the URL
     */
    open protected fun check(url: KrawlUrl, statusCode: Int, queueEntry: KrawlQueueEntry) {
        return
    }

    /**
     * Function is called before creating a KrawlQueueEntry and is used to determine the entry's priority.
     *
     * @param url KrawlUrl: The url to be prioritized
     * @param depth Int: The depth of the node that url points to
     * @param parent KrawlHistoryEntry: The parent page that this link was harvested from
     *
     * @return a byte value between 0 - 255, where 0 is the highest priority and 255 is the lowest
     */
    open protected fun assignQueuePriority(url: KrawlUrl, depth: Int, parent: KrawlHistoryEntry): Byte {
        return 1
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
     * @param reason String: An error message or reason for the error.
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

    /** Function is called after all queues have been empty for at least KrawlConfig#emptyQueueWaitTime
     * seconds. By default this method shuts down the crawler.
     */
    open protected fun onEmptyQueueTimeout() {
        return
    }

    /**
     * Submits url for crawling. This method can be called during an active crawl to add additional
     * URLs to the queue.
     *
     * @param url String: A URL to add to the queue
     * @param priority Byte: A priority value from -128 to 127, where -128 is the highest priority
     * @param beforeSchedule (KrawlQueueEntry) -> Unit: Function to run after a queue entry has been created but
     * prior to the entry being pushed to the queue. This facilitates additional book keeping.
     *
     * @return id of this root page in the rootPageUrl -> Id map
     */
    fun submitUrl(url: String, priority: Byte = 0,
                  beforeSchedule: (KrawlQueueEntry) -> Unit = { _ -> }): Int {

        // Convert URL to KrawlUrl so we can get the canonical form
        val url = KrawlUrl.new(url)

        val rootPageId: Int = maximumUsedId.getAndIncrement()
        _rootPageIds[url.rawUrl] = rootPageId

        val queueEntry = KrawlQueueEntry(url.canonicalForm, rootPageId, priority = priority)

        beforeSchedule(queueEntry)

        scheduledQueue.push(url.domain, listOf(queueEntry))

        return rootPageId
    }

    /**
     * Removes all queue entries that stemmed from rootUrl.
     *
     * @param rootUrl String: The origin URL of a crawl
     * @return the number of entries removed from the queue
     */
    fun removeUrlsByRootPage(rootUrl: String): Int {
        val id: Int = _rootPageIds[rootUrl] ?: return 0

        return scheduledQueue.deleteByRootPageId(id)
    }

    /**
     * Remove all queue entries that were inserted before beforeTime.
     *
     * @param beforeTime LocalDateTime: Time before which all entries are removed
     * @return the number of entries removed from the queue
     */
    fun removeUrlsByAge(beforeTime: LocalDateTime): Int = scheduledQueue.deleteByAge(beforeTime)

    /**
     * Starts the Krawler with the URLs provided. This method will call `onCrawlStart()`
     * perform the crawl and then call `onCrawlEnd()`. This method will block for the duration
     * of the crawl.
     *
     * @param: seedUrl List<String>: A list of seed URLs
     * @param: blocking [Boolean]: (default true) whether to block until completion or immediately return
     *
     */
    fun start(seedUrl: List<String>, blocking: Boolean = true) = runBlocking(CommonPool) {
        // Convert all URLs to KrawlUrls
        val krawlUrls: List<KrawlUrl> = seedUrl.map { KrawlUrl.new(it) }

        krawlUrls.forEach {
            val rootPageId: Int = maximumUsedId.getAndIncrement()
            _rootPageIds[it.rawUrl] = rootPageId
            scheduledQueue.push(it.domain, listOf(KrawlQueueEntry(it.canonicalForm, rootPageId)))
        }

        onCrawlStart()
        val urls: Channel<KrawlQueueEntry> = scheduledQueue.krawlQueueEntryChannel
		repeat(krawlQueues!!.size) {
		    launch(CommonPool + job) {
    		    val actions: ProducerJob<KrawlAction> = produceKrawlActions(urls)
	    		doCrawl(actions)
			}
		}

        if (blocking)
            job.join()
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
        start(seedUrl, false)
    }


    /**
     * Returns true if the threadpool is still actively running tasks, false otherwise
     *
     * @return: true if threadpool is active, false otherwise
     */
    fun isActive(): Boolean {
        return job.isActive
    }

    /**
     * Attempts to stop Krawler by gracefully shutting down. All current threads will finish
     * executing.
     */
    fun stop() = job.cancel()

    /**
     * Private members
     */

    internal sealed class KrawlAction {
        data class Visit(val krawlUrl: KrawlUrl, val doc: KrawlDocument, val queueEntry: KrawlQueueEntry): KrawlAction()
        data class Check(val krawlUrl: KrawlUrl, val statusCode: Int, val queueEntry: KrawlQueueEntry): KrawlAction()
        class Noop: KrawlAction()
    }

    internal val visitCount: AtomicInteger = AtomicInteger(0)

    internal suspend fun produceKrawlActions(entries: ReceiveChannel<KrawlQueueEntry>): ProducerJob<KrawlAction>
            = produce(CommonPool + job) {

		while(true) {
			// This is where we'll die bomb out if we don't receive an entry after some time
			var timeoutCounter: Long = 0
    	    while(entries.isEmpty) {
			    if (timeoutCounter++ == config.emptyQueueWaitTime) {
                    onEmptyQueueTimeout()

                    if (config.shutdownOnEmptyQueue) {
                        logger.debug("Closing channel after timeout reached")
                        channel.close()
                        job.cancel()
                        return@produce
                    }
				}
				delay(1000)
			}

            val queueEntry = entries.receive()
            val action: KrawlAction  = fetch(queueEntry).await()

            if (action !is KrawlAction.Noop) {
			    if (visitCount.getAndIncrement() >= config.totalPages && config.totalPages > 0) {
                    logger.debug("Closing produceKrawlActions")
					job.cancel()
					return@produce
                }
			}
			
            send(action)
		}
    }

    internal fun fetch(queueEntry: KrawlQueueEntry): Deferred<KrawlAction>
            = async(CommonPool + job) {

        val (url, rootPageId, _, depth) = queueEntry

        val krawlUrl: KrawlUrl = KrawlUrl.new(url)
        val parent: KrawlUrl = KrawlUrl.new(queueEntry.parent.url)

        // Make sure we're within depth limit
        if (depth >= config.maxDepth && config.maxDepth != -1) {
            logger.debug("Max depth!")
            return@async KrawlAction.Noop()
        }

        // Do a history check
        val history: KrawlHistoryEntry =
                if (krawlHistory!!.hasBeenSeen(krawlUrl)) { // If it has been seen
                    onRepeatVisit(krawlUrl, parent)
                    logger.debug("History says no")
                    return@async KrawlAction.Noop()
                } else {
                    krawlHistory!!.insert(krawlUrl)
                }

        val visit = shouldVisit(krawlUrl, queueEntry)
        val check = shouldCheck(krawlUrl, queueEntry)

        if (visit || check) {
            // If we're respecting robots.txt check if it's ok to visit this page
            if (config.respectRobotsTxt && !minder.isSafeToVisit(krawlUrl)) {
                logger.debug("Robots says no")
                return@async KrawlAction.Noop()
            }

            val doc: RequestResponse = if (visit) {
                requestProvider.getUrl(krawlUrl)
            } else {
                requestProvider.checkUrl(krawlUrl)
            }

            // If there was an error on trying to get the doc, call content fetch error
            if (doc is ErrorResponse) {
                onContentFetchError(krawlUrl, doc.reason)
                logger.debug("Content fetch error!")
                return@async KrawlAction.Noop()
            }

            // If there was an error parsing the response, still a content fetch error
            if (doc !is KrawlDocument) {
                onContentFetchError(krawlUrl, "Krawler was unable to parse the response from the server.")
                logger.debug("Content fetch error!")
                return@async KrawlAction.Noop()
            }

            val links = harvestLinks(doc, krawlUrl, history, depth, rootPageId)
            scheduledQueue.push(krawlUrl.domain, links)

            if (visit)
                return@async KrawlAction.Visit(krawlUrl, doc, queueEntry)
            else
                return@async KrawlAction.Check(krawlUrl, doc.statusCode, queueEntry)
        }

        return@async KrawlAction.Noop()
    }

    // Set of redirect codes
    private val redirectCodes: Set<Int> = setOf(300, 301, 302, 303, 307, 308)

    internal suspend fun doCrawl(channel: ReceiveChannel<KrawlAction>) {
        channel.consumeEach { action ->
            when(action) {
                is KrawlAction.Visit ->
                    async(CommonPool + job) {
                        visit(action.krawlUrl, action.doc, action.queueEntry)
                    }.await()
                is KrawlAction.Check ->
                    async(CommonPool + job) {
                        check(action.krawlUrl, action.statusCode, action.queueEntry)
                    }.await()
            }
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
                                      history: KrawlHistoryEntry, depth: Int, rootPageId: Int): List<KrawlQueueEntry> {

        // Handle redirects by getting the location tag of the header and pushing that into the queue
        if (!config.useFastRedirectStrategy && doc.statusCode in redirectCodes && config.followRedirects) {
            // Queue the redirected URL
            val locStr: String = doc.headers["location"] ?: return listOf()
            val location: KrawlUrl = KrawlUrl.new(locStr, url)

            // We won't count it as a visit since we didn't get any content
            visitCount.decrementAndGet()

            return listOf(KrawlQueueEntry(location.canonicalForm, rootPageId, history, depth))
        }

        // If it wasn't a redirect parse out the URLs from anchor tags and construct queue entries from them
        return listOf(
                // Anchor tags
                doc.anchorTags
                        .filterNot { it.attr("href").startsWith("#") }
                        .filter { it.attr("href").length <= 2048 }
                        .map { KrawlUrl.new(it.attr("href"), url) }
                        .filter { it != InvalidKrawlUrl && it.canonicalForm.isNotBlank() }
                        // TODO: Add in priority call?
                        .map { KrawlQueueEntry(it.canonicalForm, rootPageId, history, depth + 1,
                                assignQueuePriority(it, depth, history)) },
                // Everything else (img tags, scripts, etc)d
                doc.otherOutgoingLinks
                        .filterNot { it.startsWith("#")}
                        .filter { it.length <= 2048 }
                        .map { KrawlUrl.new(it, url) }
                        .map { KrawlQueueEntry(it.canonicalForm, rootPageId, history, depth + 1,
                                assignQueuePriority(it, depth, history))}
        ).flatten()
    }
}
