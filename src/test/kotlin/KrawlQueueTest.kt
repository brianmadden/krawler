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
import io.thelandscape.krawler.crawler.History.KrawlHistoryEntry
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueHSQLDao
import io.thelandscape.krawler.crawler.KrawlQueue.QueueEntry
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Dao tests
class KrawlQueueHSQLDaoTest {

    val connection: Connection = DriverManager.getConnection("jdbc:hsqldb:mem:testdb", "SA", "")
    val session: Session = DefaultSession(connection, HsqlDialect())

    val dao: KrawlQueueHSQLDao = KrawlQueueHSQLDao(session)

    @Before fun setUp() {
        session.update("CREATE TABLE IF NOT EXISTS krawlHistory " +
                "(id INT IDENTITY, url VARCHAR(255), timestamp TIMESTAMP)")
        session.update("CREATE TABLE IF NOT EXISTS krawlQueue " +
                "(url VARCHAR(255) NOT NULL PRIMARY KEY, parent INT, depth INT, timestamp TIMESTAMP)")

    }

    @Test fun testPush() {
        val urls: List<QueueEntry> = listOf(
                QueueEntry("http://www.google.com"),
                QueueEntry("http://www.a.com"),
                QueueEntry("http://www.b.com", KrawlHistoryEntry(0, "http://www.a.com"), 1))
        val res = dao.push(urls)
        assertTrue { res.isNotEmpty() }

    }

    @Test fun testPop() {
        val noParent = KrawlHistoryEntry()
        val zParent = KrawlHistoryEntry(0, "http://www.y.com")
        val list = listOf(
                QueueEntry("http://www.x.com", noParent),
                QueueEntry("http://www.y.com", noParent),
                QueueEntry("http://www.z.com", zParent, 1))
        dao.push(list)

        var popped = dao.pop()
        assertNotNull( popped )
        assertEquals("http://www.x.com", popped?.url)
        assertEquals(noParent, popped?.parent)

        popped = dao.pop()
        assertNotNull( popped )
        assertEquals("http://www.y.com", popped?.url)
        assertEquals(noParent, popped?.parent)

        popped = dao.pop()
        assertNotNull( popped )
        assertEquals("http://www.z.com", popped?.url)
        assertEquals(zParent, popped?.parent)

        popped = dao.pop()
        assertNull( popped )
    }
}