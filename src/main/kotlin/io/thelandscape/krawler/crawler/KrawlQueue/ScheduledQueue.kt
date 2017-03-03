/**
 * Created by brian.a.madden@gmail.com on 1/28/17.
 *
 * Copyright (c) <2017> <H, llc>
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

package io.thelandscape.krawler.crawler.KrawlQueue

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.thelandscape.krawler.crawler.KrawlConfig
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.concurrent.TimeUnit

class ScheduledQueue(private val queues: List<KrawlQueueIf>, private val config: KrawlConfig) {

    val logger: Logger = LogManager.getLogger()

    private val popLock: Any = Any()
    private var popSelector: Int = 0

    private val pushLock: Any = Any()
    private var pushSelector: Int = 0

    private val pushAffinityCache: LoadingCache<String, Int> = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(
                    object : CacheLoader<String, Int>() {
                        override fun load(key: String): Int {
                            return synchronized(pushLock) { pushSelector++ % queues.size }
                        }
                    }
            )

    /**
     * Pushes KrawlQueueEntries to the queue that it has affinity with.
     * This should help create better parallelism when crawling multiple domains.
     *
     * @param referringDomain [String]: The referring domain to determine queue affinity
     * @param entries [List]: List of KrawlQueueEntries to push to the appropriate queue
     *
     * @return [List]: List of KrawlQueueEntries that were pushed
     */
    fun push(referringDomain: String, entries: List<KrawlQueueEntry>): List<KrawlQueueEntry> {
        val affinity = pushAffinityCache[referringDomain]
        return queues[affinity].push(entries)
    }

    /**
     * Pops a KrawlQueueEntry from the first queue with an entry available. This method
     * will rotate through the queues in round robin fashion to try to increase parallelism.
     *
     * @return [KrawlQueueEntry?]: A single KrawlQueueEntry if available, null otherwise
     */
    fun pop(): KrawlQueueEntry? {
        var emptyQueueWaitCount: Long = 0

        // This should only be 0 in the case of testing, so this is kind of a hack
        val modVal = if (pushSelector > queues.size || pushSelector == 0)
            queues.size
        else
            pushSelector

        // Pop a URL off the queue
        var entry: KrawlQueueEntry? = synchronized(popLock) { queues[popSelector++ % modVal].pop() }

        // Multiply by queue size, we'll check all of the queues each second
        while (entry == null && emptyQueueWaitCount < (config.emptyQueueWaitTime * modVal)) {
            logger.debug("Waiting on an entry... Wait count: ${emptyQueueWaitCount / modVal}")
            // Wait for the configured period for more URLs
            Thread.sleep(Math.ceil(1000.0 / modVal).toLong())
            emptyQueueWaitCount++

            // Try to pop again
            entry = synchronized(popLock) { queues[popSelector++ % modVal].pop() }
        }

        return entry
    }
}