package io.thelandscape.krawler.crawler.KrawlQueue

import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.core.ThreadLocalSession
import com.github.andrewoma.kwery.core.dialect.HsqlDialect
import com.github.andrewoma.kwery.mapper.*
import com.github.andrewoma.kwery.mapper.util.camelToLowerUnderscore
import com.mchange.v2.c3p0.ComboPooledDataSource
import java.time.LocalDateTime

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


object krawlQueueTable : Table<QueueEntry, String>("krawlQueue", TableConfiguration(standardDefaults + timeDefaults,
        standardConverters + timeConverters, camelToLowerUnderscore)) {

    val Url by col(QueueEntry::url, id = true)
    val Depth by col(QueueEntry::depth)
    val Timestamp by col(QueueEntry::timestamp)

    override fun idColumns(id: String) = setOf(Url of id)

    override fun create(value: Value<QueueEntry>) = QueueEntry(value of Url, value of Depth, value of Timestamp)

}

// TODO: Move this somewhere better
private class HSQLConnection(fileBacked: Boolean, fileName: String = ".krawl_tmp") {
    val cpds: ComboPooledDataSource = ComboPooledDataSource()

    init {
        if (fileBacked)
            cpds.jdbcUrl = "jdbc:hsqldb:file:$fileName"
        else
            cpds.jdbcUrl = "jdbc:hsqldb:mem:${LocalDateTime.now().hashCode()}"
        cpds.user = ""
        cpds.password = ""
        cpds.maxConnectionAge = 600
        cpds.isTestConnectionOnCheckin = true
        cpds.idleConnectionTestPeriod = 500
    }
}

private val connection = HSQLConnection(false).cpds
private val session: ThreadLocalSession = ThreadLocalSession(connection, HsqlDialect())

internal val KrawlQueueDao = KrawlQueueHSQLDao(session)

class KrawlQueueHSQLDao(session: Session):
        KrawlQueueIf, AbstractDao<QueueEntry, String>(session, krawlQueueTable, QueueEntry::url) {

    override fun pop(n: Int): List<QueueEntry> {
        val sql = "SELECT $columns FROM ${table.name} WHERE depth = MIN(depth) LIMIT :n"
        val params = mapOf("n" to n)

        return session.select(sql, params, options("popMinDepth"), table.rowMapper())
    }

    override fun push(urls: List<QueueEntry>): List<Pair<Int, QueueEntry>> {
        val sql: String = "INSERT INTO ${table.name} ($columns) VALUES(:url, :depth, :ts)"
        val values: List<Map<String, Any>> = urls.map {
            mapOf("url" to it.url,
                    "depth" to it.depth,
                    "ts" to it.timestamp)
        }
        return session.batchInsert(sql, values, f = table.rowMapper())
    }
}