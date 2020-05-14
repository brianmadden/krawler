package io.thelandscape

import com.github.andrewoma.kwery.core.DefaultSession
import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.core.dialect.HsqlDialect
import com.github.andrewoma.kwery.core.interceptor.LoggingInterceptor
import io.thelandscape.krawler.crawler.History.KrawlHistoryEntry
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueEntry
import io.thelandscape.krawler.crawler.KrawlQueue.KrawlQueueHSQLDao
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import kotlin.test.assertEquals

class KrawlQueueDaoTest {
    val connection: Connection = DriverManager.getConnection("jdbc:hsqldb:mem:testdb", "", "")
    val session: Session = DefaultSession(connection, HsqlDialect(), LoggingInterceptor())

    val queueDao: KrawlQueueHSQLDao = KrawlQueueHSQLDao("test", session)

    @Before fun setUp() {
        session.update("DROP TABLE test")
        session.update("CREATE TABLE IF NOT EXISTS test " +
        "(url VARCHAR(2048) NOT NULL, root_page_id INT, io.thelandscape.parent INT, depth INT, priority TINYINT, timestamp TIMESTAMP)")
    }

    @Test fun tesPush() {
        val pushers = listOf(KrawlQueueEntry("http://www.test.com/", 0, KrawlHistoryEntry()))
        val res = queueDao.push(pushers)
        assertEquals(res, pushers)
    }

    @Test fun testPop() {
        val pushers = listOf(KrawlQueueEntry("http://www.test.com/", 0, KrawlHistoryEntry()))
        queueDao.push(pushers)
        val res = queueDao.pop()
        assertEquals(res, pushers.first())
    }

    @Test fun testDeleteByRootPageId() {
        val pushers = listOf(
                KrawlQueueEntry("http://www.a1.com/", 0, KrawlHistoryEntry()),
                KrawlQueueEntry("http://www.b2.com/", 0, KrawlHistoryEntry()),
                KrawlQueueEntry("http://www.c3.com/", 2, KrawlHistoryEntry()),
                KrawlQueueEntry("http://www.d4.com/", 2, KrawlHistoryEntry()),
                KrawlQueueEntry("http://www.e5.com/", 0, KrawlHistoryEntry())
                )
        queueDao.push(pushers)
        val res = queueDao.deleteByRootPageId(2)
        assertEquals(2, res)
    }

    @Test fun testDeleteByAge() {
        val pushers = listOf(
                KrawlQueueEntry("http://www.a1.com/", 0, KrawlHistoryEntry(), timestamp = LocalDateTime.now()),
                KrawlQueueEntry("http://www.b2.com/", 0, KrawlHistoryEntry(), timestamp = LocalDateTime.now()),
                KrawlQueueEntry("http://www.c3.com/", 2, KrawlHistoryEntry(), timestamp = LocalDateTime.now().minusDays(2)),
                KrawlQueueEntry("http://www.d4.com/", 2, KrawlHistoryEntry(), timestamp = LocalDateTime.now().minusDays(2)),
                KrawlQueueEntry("http://www.e5.com/", 0, KrawlHistoryEntry(), timestamp = LocalDateTime.now())
        )
        queueDao.push(pushers)
        val res = queueDao.deleteByAge(LocalDateTime.now().minusDays(1))
        assertEquals(2, res)
    }
}
