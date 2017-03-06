/**
 * Created by brian.a.madden@gmail.com on 12/23/16.
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

import io.thelandscape.krawler.crawler.KrawlConfig
import io.thelandscape.krawler.crawler.Krawler
import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import java.time.LocalTime
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger

class SimpleExample(config: KrawlConfig = KrawlConfig()) : Krawler(config) {

    private val FILTERS: Regex = Regex(".*(\\.(css|js|bmp|gif|jpe?g|png|tiff?|mid|mp2|mp3|mp4|wav|avi|" +
            "mov|mpeg|ram|m4v|pdf|rm|smil|wmv|swf|wma|zip|rar|gz|tar|ico))$", RegexOption.IGNORE_CASE)

    /**
     * Threadsafe whitelist of acceptable hosts to visit
     */
    val whitelist: MutableSet<String> = ConcurrentSkipListSet()

    override fun shouldVisit(url: KrawlUrl): Boolean {
        val withoutGetParams: String = url.canonicalForm.split("?").first()
        return (!FILTERS.matches(withoutGetParams) && url.host in whitelist)
    }

    private val counter: AtomicInteger = AtomicInteger(0)

    override fun visit(url: KrawlUrl, doc: KrawlDocument) {
        println("${counter.incrementAndGet()}. Crawling ${url.canonicalForm}")
    }

    override fun onContentFetchError(url: KrawlUrl, reason: String) {
        println("${counter.incrementAndGet()}. Tried to crawl ${url.canonicalForm} but failed to read the content.")
    }

    private var startTimestamp: Long = 0
    private var endTimestamp: Long = 0

    override fun onCrawlStart() {
        startTimestamp = LocalTime.now().toNanoOfDay()
    }
    override fun onCrawlEnd() {
        endTimestamp = LocalTime.now().toNanoOfDay()
        println("Crawled $counter pages in ${(endTimestamp - startTimestamp) / 1000000000.0} seconds. ${visitCount.get()}")
    }
}