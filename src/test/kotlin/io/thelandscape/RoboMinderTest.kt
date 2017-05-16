package io.thelandscape

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

import com.nhaarman.mockito_kotlin.MockitoKotlin
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.thelandscape.krawler.http.ErrorResponse
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestProviderIf
import io.thelandscape.krawler.http.RequestResponse
import io.thelandscape.krawler.robots.RoboMinder
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val invalidUrl = KrawlUrl.new("http://valid.url/invalid/")
private val validUrl = KrawlUrl.new("http://valid.url/valid")
val noResponse: RequestResponse = ErrorResponse(nullUrl)

class RoboMinderTest {

    val mockRequests = mock<RequestProviderIf>()
    val minder = RoboMinder("AGENT-A", mockRequests)

    init {
        MockitoKotlin.registerInstanceCreator { KrawlUrl.new("") }
    }

    @Test fun testFetch() = runBlocking<Unit> {
        minder.fetch("http://www.google.com")
        verify(mockRequests).fetchRobotsTxt(KrawlUrl.new("http://www.google.com/robots.txt"))
    }

    @Test fun testProcess() {
        // Verify io.thelandscape.getDisallowAll returns false
        var resp = minder.process(disallowAll)
        assertFalse { resp("") }

        // Verify disallow me returns false for AGENT-A
        resp = minder.process(disallowMe)
        assertFalse { resp("") }

        // Verify io.thelandscape.getAllowMe returns true for AGENT-A
        resp = minder.process(allowMe)
        assertTrue { resp("") }

        // Verify io.thelandscape.getAllowAll allows me
        resp = minder.process(allowAll)
        assertTrue { resp("") }

        // Verify unrelated rules don't affect me
        resp = minder.process(unrelatedResponse)
        assertTrue { resp("") }

        // Verify no response doesn't affect me
        resp = minder.process(noResponse)
        assertTrue { resp("") }

        // Verify specific bans are respected
        resp = minder.process(specificAgentSpecificPage)
        // This invalid should be false
        assertFalse { resp(invalidUrl.path) }
        // This valid should be true
        assertTrue { resp(validUrl.path) }
    }

    /** TODO: Turn this back on when mockito is updated to support coroutines
     * https://discuss.kotlinlang.org/t/verifying-suspending-functions-with-mockito-or-alternatives/2492/2
    @Test fun isSafeToVisit() = runBlocking<Unit> {
        //whenever(mockRequests.fetchRobotsTxt(any())).thenReturn(specificAgentSpecificPage)

        val valid = minder.isSafeToVisit(validUrl)
        val invalid = minder.isSafeToVisit(invalidUrl)

        assertTrue(valid)
        assertFalse(invalid)
    }
    */
}