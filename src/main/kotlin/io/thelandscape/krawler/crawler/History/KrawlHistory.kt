/**
 * Created by brian.a.madden@gmail.com on 11/24/16.
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

package io.thelandscape.krawler.crawler.History

import io.thelandscape.krawler.hsqlSession
import io.thelandscape.krawler.http.KrawlUrl
import java.time.LocalDateTime

interface KrawlHistoryIf {

    /**
     * Inserts an entry into the history tracker
     *
     * @param url KrawlUrl: The KrawlUrl to insert
     *
     * @returns KrawlHistoryEntry that was inserted into the history store with generated ID
     */
    fun insert(url: KrawlUrl): KrawlHistoryEntry

    /**
     * Verifies that a URL has not previously been visited.
     *
     * @param url KrawlUrl: The URL to visit
     *
     * @return true if URL has not been visited previously, false otherwise
     */
    fun verifyUnique(url: KrawlUrl): Boolean

    /**
     * Clears the Krawl history prior to the timestamp specified by beforeTime.
     *
     * @param beforeTime LocalDateTime: Timestamp before which the history should be cleared
     *
     * @return the number of history entries that were cleared
     */
    fun clearHistory(beforeTime: LocalDateTime = LocalDateTime.now()): Int
}

// Instantiate the KrawlFrontier with the default DAO
internal val krawlHistory = KrawlHistoryHSQLDao(hsqlSession)