/**
 * Created by brian.a.madden@gmail.com on 10/31/16.
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

class KrawlConfig(
        // Size of crawler threadpool
        // Default: -1 (unlimited)
        val numThreads: Int = 1,
        // Maximum crawl depth
        // Default: -1 (unlimited)
        val maxDepth: Int = -1,
        // Global crawl limit -- if this is reached, shutdown
        // Default: -1 (unlimited)
        val totalPages: Int = -1,
        // Politeness delay (in ms) to wait between requests
        // Default: 200ms
        val politenessDelay: Long = 200,
        // User agent string
        val userAgent: String = "io.thelandscape.Krawler Web Crawler Framework",
        // Directory where KrawlQueue will be persisted
        val crawlDirectory: String = ".krawl",
        // Should the Krawl progress persist?
        val persistentCrawl: Boolean = false,
        // Maximum number of queued URLs
        val maximumQueueSize: Int = 50000,
        // Length of time (in seconds) to wait before giving up and calling it quits
        emptyQueueWaitTime: Long = 10,
        // The timeout in milliseconds used when requesting a connection. 0 = infinite, -1 = system default
        // Default: -1
        val connectionRequestTimeout: Int = -1,
        // Maximum period inactivity between two consecutive data packets. 0 = infinite, -1 = system default
        // Default: -1
        val socketTimeout: Int = -1,
        // Connect timeout in milliseconds until a connection is established. 0 = infinite, -1 = system default
        val connectTimeout: Int = -1,
        // Should we respect robots.txt
        val respectRobotsTxt: Boolean = true) {
    // Length of time to sleep when queue becomes empty
    var emptyQueueWaitTime: Long = emptyQueueWaitTime
        private set(value) { field = if (value <= 0) 1 else value }
}