/**
 * Created by brian.a.madden@gmail.com on 1/15/17.
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

import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.robots.RobotsTxt
import org.junit.Test
import kotlin.test.assertEquals

val nullUrl = KrawlUrl.new("")
val disallowAll: RobotsTxt = RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: * \n Disallow: / "))
val disallowMe: RobotsTxt =
        RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: AGENT-A \n Disallow: / \n User-Agent: * \n Disallow: "))
val allowMe: RobotsTxt =
        RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: AGENT-A \n Disallow: \n User-Agent: * \n Disallow: /"))
val allowAll: RobotsTxt = RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: * \n Disallow: "))
val unrelatedResponse: RobotsTxt = RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: Google \n Disallow: /"))
val specificAgentSpecificPage: RobotsTxt =
        RobotsTxt(nullUrl, prepareResponse(200, "User-Agent: AGENT-A \n Disallow: /invalid"))

class RobotsTxtTest {

    @Test fun verifyUserAgents() {
        assertEquals(setOf("*"), allowAll.disallowRules.keys)
        assertEquals(setOf("AGENT-A", "*"), allowMe.disallowRules.keys)
        assertEquals(setOf("*"), disallowAll.disallowRules.keys)
        assertEquals(setOf("AGENT-A", "*"), disallowMe.disallowRules.keys)
        assertEquals(setOf("Google"), unrelatedResponse.disallowRules.keys)
        assertEquals(setOf("AGENT-A"), specificAgentSpecificPage.disallowRules.keys)
    }

    @Test fun verifyDisallowRules() {
        assertEquals(setOf(""), allowAll.disallowRules["*"] as Set<String>)

        assertEquals(setOf("/"), disallowMe.disallowRules["AGENT-A"] as Set<String>)
        assertEquals(setOf(""), disallowMe.disallowRules["*"] as Set<String>)

        assertEquals(setOf(""), allowMe.disallowRules["AGENT-A"] as Set<String>)
        assertEquals(setOf("/"), allowMe.disallowRules["*"] as Set<String>)

        assertEquals(setOf(""), allowAll.disallowRules["*"] as Set<String>)

        assertEquals(setOf("/"), unrelatedResponse.disallowRules["Google"] as Set<String>)

        assertEquals(setOf("/invalid"), specificAgentSpecificPage.disallowRules["AGENT-A"] as Set<String>)
    }

}