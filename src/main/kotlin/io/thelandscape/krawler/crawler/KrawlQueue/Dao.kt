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

package io.thelandscape.krawler.crawler.KrawlQueue

import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.fetcher.GraphFetcher
import com.github.andrewoma.kwery.fetcher.Node
import com.github.andrewoma.kwery.fetcher.Property
import com.github.andrewoma.kwery.fetcher.Type
import com.github.andrewoma.kwery.mapper.*
import com.github.andrewoma.kwery.mapper.util.camelToLowerUnderscore
import io.thelandscape.krawler.crawler.History.KrawlHistoryEntry
import io.thelandscape.krawler.crawler.History.KrawlHistoryHSQLDao
import kotlinx.coroutines.experimental.sync.Mutex
import java.util.concurrent.TimeUnit

object historyConverter :
        SimpleConverter<KrawlHistoryEntry>( { row, c -> KrawlHistoryEntry(row.long(c)) }, KrawlHistoryEntry::id)

class KrawlQueueTable(name: String) : Table<KrawlQueueEntry, String>(name,
        TableConfiguration(standardDefaults + timeDefaults + reifiedValue(KrawlHistoryEntry()),
                standardConverters + timeConverters + reifiedConverter(historyConverter), camelToLowerUnderscore)) {

    val Url by col(KrawlQueueEntry::url, id = true)
    val Parent by col (KrawlQueueEntry::parent)
    val Depth by col(KrawlQueueEntry::depth)
    val Timestamp by col(KrawlQueueEntry::timestamp)

    override fun idColumns(id: String) = setOf(Url of id)

    override fun create(value: Value<KrawlQueueEntry>) =
            KrawlQueueEntry(value of Url, value of Parent, value of Depth, value of Timestamp)

}

// TODO: Figure out how to allow this to take a generic KrawlHistoryIf
// rather than an HSQLDao while keeping the interface clean
class KrawlQueueHSQLDao(name: String,
                        session: Session,
                        private val histDao: KrawlHistoryHSQLDao):
        KrawlQueueIf, AbstractDao<KrawlQueueEntry, String>(session, KrawlQueueTable(name), KrawlQueueEntry::url) {

    init {
        // Create queue table
        session.update("CREATE TABLE IF NOT EXISTS $name " +
                "(url VARCHAR(2048) NOT NULL, parent INT, depth INT, timestamp TIMESTAMP)")
    }

    private val syncMutex = Mutex()
    override suspend fun pop(): KrawlQueueEntry? {
        val historyEntry = Type(KrawlHistoryEntry::id, { histDao.findByIds(it) })
        val queueEntry = Type(
                KrawlQueueEntry::url,
                { this.findByIds(it) },
                listOf(Property(KrawlQueueEntry::parent,  historyEntry, { it.parent.id }, { c, t -> c.copy(parent = t) })))

        val fetcher: GraphFetcher = GraphFetcher(setOf(queueEntry, historyEntry))

        fun <T> Collection<T>.fetch(node: Node) = fetcher.fetch(this, Node(node))

        val selectSql = "SELECT TOP 1 $columns FROM ${table.name}"
        var out: List<KrawlQueueEntry> = listOf()
        // Synchronize this to prevent race conditions between popping and deleting
        try {
            syncMutex.lock()
            out = session.select(selectSql, mapper = table.rowMapper())
            if (out.isNotEmpty())
                session.update("DELETE FROM ${table.name} WHERE url = :id", mapOf("id" to out.first().url))
        } finally {
            syncMutex.unlock()
        }

        return out.fetch(Node.all).firstOrNull()
    }

    override suspend fun push(urls: List<KrawlQueueEntry>): List<KrawlQueueEntry> {
        if (urls.isNotEmpty())
            return this.batchInsert(urls)

        return listOf()
    }
}