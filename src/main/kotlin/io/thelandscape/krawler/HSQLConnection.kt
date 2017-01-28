/**
 * Created by brian.a.madden@gmail.com on 11/25/16.
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

package io.thelandscape.krawler

import com.github.andrewoma.kwery.core.ThreadLocalSession
import com.github.andrewoma.kwery.core.dialect.HsqlDialect
import com.github.andrewoma.kwery.core.interceptor.LoggingInterceptor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDateTime


internal class HSQLConnection(fileBacked: Boolean, fileName: String = ".krawl_tmp") {

    var ds: HikariDataSource
        private set

    var hsqlSession: ThreadLocalSession
        private set

    init {
        Class.forName("org.hsqldb.jdbcDriver")

        val config = HikariConfig()

        if (fileBacked)
            config.jdbcUrl = "jdbc:hsqldb:file:$fileName"
        else
            config.jdbcUrl = "jdbc:hsqldb:mem:${LocalDateTime.now().hashCode()}"

        config.username = ""
        config.password = ""
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

        ds = HikariDataSource(config)
        hsqlSession = ThreadLocalSession(ds, HsqlDialect(), LoggingInterceptor())
    }


}