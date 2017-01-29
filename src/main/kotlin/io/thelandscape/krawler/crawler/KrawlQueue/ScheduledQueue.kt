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

import io.thelandscape.krawler.crawler.KrawlConfig
import java.util.concurrent.atomic.AtomicInteger

class ScheduledQueue(private val queues: List<KrawlQueueIf>, private val config: KrawlConfig) {

    private var selector: AtomicInteger = AtomicInteger(0)

    fun pop(): KrawlQueueEntry? {
        var emptyQueueWaitCount: Long = 0
        // Pop a URL off the queue
        var entry: KrawlQueueEntry? = queues[selector.incrementAndGet() % queues.size].pop()

        // Multiply by queue size, we'll check all of the queues each second
        while (entry == null && emptyQueueWaitCount < (config.emptyQueueWaitTime * queues.size)) {
            // Wait for the configured period for more URLs
            Thread.sleep(Math.ceil(1000.0 / queues.size).toLong())
            emptyQueueWaitCount++

            // Try to pop again
            entry = queues[selector.incrementAndGet() % queues.size].pop()
        }

        return entry
    }
}