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

import com.nhaarman.mockito_kotlin.*
import io.thelandscape.krawler.http.ErrorResponse
import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestProviderIf
import io.thelandscape.krawler.http.RequestResponse
import io.thelandscape.krawler.robots.RoboMinder
import io.thelandscape.krawler.robots.RobotsTxt
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val nullUrl = KrawlUrl.new("")
private val invalidUrl = KrawlUrl.new("http://valid.url/invalid/")
private val validUrl = KrawlUrl.new("http://valid.url/valid")
private val disallowAll: RobotsTxt = RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: * \n Disallow: / "))
private val disallowMe: RobotsTxt =
        RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: AGENT-A \n Disallow: / \n User-Agent: * \n Disallow: "))
private val allowMe: RobotsTxt =
        RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: AGENT-A \n Disallow: \n User-Agent: * \n Disallow: /"))
private val allowAll: RobotsTxt = RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: * \n Disallow: "))
private val unrelatedResponse: RobotsTxt = RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: Google \n Disallow: /"))
private val specificAgentSpecificPage: RobotsTxt =
        RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: AGENT-A \n Disallow: /invalid"))
private val noResponse: RequestResponse = ErrorResponse(nullUrl)


class RoboMinderTest {

    val mockRequests = mock<RequestProviderIf>()
    val minder = RoboMinder("AGENT-A", mockRequests)

    init {
        MockitoKotlin.registerInstanceCreator { KrawlUrl.new("") }
    }

    @Test fun testFetch() {
        minder.fetch("http://www.google.com")
        verify(mockRequests).fetchRobotsTxt(KrawlUrl.new("http://www.google.com/robots.txt"))
    }

    @Test fun testProcess() {
        // Verify disallowAll returns false
        var resp = minder.process(disallowAll)
        assertFalse { resp("") }

        // Verify disallow me returns false for AGENT-A
        resp = minder.process(disallowMe)
        assertFalse { resp("") }

        // Verify allowMe returns true for AGENT-A
        resp = minder.process(allowMe)
        assertTrue { resp("") }

        // Verify allowAll allows me
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

    @Test fun isSafeToVisit() {
        whenever(mockRequests.fetchRobotsTxt(any())).thenReturn(specificAgentSpecificPage)
        assertTrue { minder.isSafeToVisit(validUrl) }
        assertFalse { minder.isSafeToVisit(invalidUrl) }
    }
}