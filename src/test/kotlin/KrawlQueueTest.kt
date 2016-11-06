/**
 * Created by brian.a.madden@gmail.com on 11/3/16.
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
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueue
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueHSQLDao
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueIf
import io.thelandscape.krawler.crawler.KrawlQueue.QueueEntry
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// KrawlQueue test
class KrawlQueueTest {

    val krawlQueueDao: KrawlQueueIf = mock()
    val krawlQueue: KrawlQueue = KrawlQueue(krawlQueueDao)

    @Test fun pushTest() {
        val urls = listOf(
                QueueEntry("http://www.a.com", 0),
                QueueEntry("http://www.b.com", 0)
        )
        krawlQueue.push(urls)

        verify(krawlQueueDao, times(1)).push(urls)
    }

    @Test fun popTest() {
        val numToPop: Int = 3
        krawlQueue.pop(numToPop)

        verify(krawlQueueDao, times(1)).pop(numToPop)
    }
}


// Dao tests
class KrawlQueueHSQLDaoTest {

    val connection: Connection = DriverManager.getConnection("jdbc:hsqldb:mem:testdb", "SA", "")
    val session: Session = DefaultSession(connection, HsqlDialect())

    val dao: KrawlQueueHSQLDao = KrawlQueueHSQLDao(session)

    @Before fun setUp() {
        dao.session.update("CREATE TABLE IF NOT EXISTS krawlQueue " +
                "(url VARCHAR(255) NOT NULL PRIMARY KEY, depth INT, timestamp TIMESTAMP)",
                mapOf())
    }

    @After fun tearDown() {
        dao.session.update("SHUTDOWN")
    }

    @Test fun testPush() {
        val urls: List<QueueEntry> = listOf(
                QueueEntry("http://www.google.com", 0),
                QueueEntry("http://www.a.com", 0),
                QueueEntry("http://www.b.com", 1))
        val res = dao.push(urls)
        assertTrue { res.isNotEmpty() }

    }

    @Test fun testPop() {
        val list = listOf(
                QueueEntry("http://www.x.com", 0),
                QueueEntry("http://www.y.com", 0),
                QueueEntry("http://www.z.com", 1))
        dao.push(list)

        val popped = dao.pop(3)
        assertTrue { popped.isNotEmpty() }
        assertEquals("http://www.x.com", popped[0].url)
        assertEquals("http://www.y.com", popped[1].url)
        assertEquals("http://www.z.com", popped[2].url)
        assertEquals(0, dao.pop().size)

        dao.push(list)
        dao.pop(2)
        assertEquals(1, dao.pop().size)
    }
}