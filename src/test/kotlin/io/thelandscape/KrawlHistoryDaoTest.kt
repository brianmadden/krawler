package io.thelandscape

/**
 * Created by brian.a.madden@gmail.com on 12/24/16.
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

import com.github.andrewoma.kwery.core.DefaultSession
import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.core.dialect.HsqlDialect
import com.github.andrewoma.kwery.core.interceptor.LoggingInterceptor
import io.thelandscape.krawler.crawler.History.KrawlHistoryHSQLDao
import io.thelandscape.krawler.http.KrawlUrl
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class KrawlHistoryDaoTest {

    val connection: Connection = DriverManager.getConnection("jdbc:hsqldb:mem:testdb", "", "")
    val session: Session = DefaultSession(connection, HsqlDialect(), LoggingInterceptor())

    val krawlHistoryDao: KrawlHistoryHSQLDao = KrawlHistoryHSQLDao(session)

    @Before fun setUp() {
        session.update("DROP TABLE KrawlHistory")
        session.update("CREATE TABLE IF NOT EXISTS KrawlHistory " +
                "(id INT IDENTITY, url VARCHAR(255), timestamp TIMESTAMP)")

        val ins = KrawlUrl.new("http://www.test.com")
        val ins2 = KrawlUrl.new("http://www.test2.com")
        krawlHistoryDao.insert(ins)
        krawlHistoryDao.insert(ins2)
    }

    @Test fun testInsert() {
        // Insert a new URL
        val ins = KrawlUrl.new("http://www.test2.com")
        val ret = krawlHistoryDao.insert(ins)

        // The ID should be 2 since the inserts in setUp should be 1 & 2
        assertEquals(2, ret.id)
    }

    @Test fun testClearHistory() {
        val cleared = krawlHistoryDao.clearHistory()

        assertTrue { cleared >= 1 }
    }

    @Test fun testHasBeenSeen() {
        val url = KrawlUrl.new("http://www.test.com/")
        krawlHistoryDao.insert(url)
        val wasSeen = krawlHistoryDao.hasBeenSeen(url)

        assertTrue(wasSeen)

        val wasSeen2 = krawlHistoryDao.hasBeenSeen(KrawlUrl.new("www.foo.org"))
        assertFalse(wasSeen2)
    }

}