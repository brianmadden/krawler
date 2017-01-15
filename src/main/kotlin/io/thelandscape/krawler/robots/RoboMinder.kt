/**
 * Created by brian.a.madden@gmail.com on 1/14/17.
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

package io.thelandscape.krawler.robots

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestProviderIf
import io.thelandscape.krawler.http.RequestResponse
import io.thelandscape.krawler.http.Requests

/**
 * Class for managing the fetching and minding of robots.txt
 *
 */
class RoboMinder(private val userAgent: String,
                 private val request: RequestProviderIf,
                 config: RobotsConfig = RobotsConfig()) {

    private val rules: LoadingCache<String, (String) -> Boolean> = CacheBuilder.newBuilder()
            .maximumSize(config.robotsCacheSize)
            .expireAfterAccess(config.expireAfter, config.units)
            .build(
                    object : CacheLoader<String, (String) -> Boolean>() {
                        override fun load(key: String): ((String) -> Boolean) {
                            val resp: RequestResponse = fetch(key)
                            return process(resp)
                        }
                    }
            )


    internal fun fetch(host: String): RequestResponse {
        val robotsUrl = KrawlUrl.new("$host/robots.txt")
        return request.fetchRobotsTxt(robotsUrl)
    }

    /**
     * Process the freshly fetched robots.txt. If there are no rules pertinent to us, we'll just return null
     */
    internal fun process(robotsTxt: RequestResponse): (String) -> Boolean {

        fun convertRules(rules: Set<String>): (String) -> Boolean {
            // If empty string is in the rules we're allowed to visit anything
            if ("" in rules)
                return { x -> true }

            // If * is in the rules we're not allowed to visit anything
            if ("/" in rules)
                return { x -> false }

            // If neither of the above were true then we just have to pay attention to specific rules
            // We can visit a page if the none of the rules are contained within the URL
            return { url: String -> true !in rules.map { it in url.split("/").map {"/" + it} } }

        }

        if (robotsTxt is RobotsTxt) {
            return convertRules(robotsTxt.disallowRules[userAgent] ?: robotsTxt.disallowRules["*"] ?: setOf())
        }

        return { x -> true }
    }

    /**
     * Checks the robots.txt rules for a particular host to determine if the page is safe to visit or not.
     *
     * @param url KrawlUrl: the URL in question
     *
     * @return true if safe to visit, false otherwise
     */
    fun isSafeToVisit(url: KrawlUrl): Boolean {

        // Not bothering to lock this since it should be idempotent
        val withoutGetParams: String = url.path.split("?").firstOrNull() ?: url.path

        return rules[url.host].invoke(withoutGetParams)
    }
}
