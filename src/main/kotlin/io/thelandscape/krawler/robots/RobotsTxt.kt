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

package io.thelandscape.krawler.robots

import io.thelandscape.krawler.http.KrawlUrl
import io.thelandscape.krawler.http.RequestResponse
import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

class RobotsTxt(url: KrawlUrl, response: HttpResponse) : RequestResponse {

    // TODO: Improve handling: https://en.wikipedia.org/wiki/Robots_exclusion_standard#About_the_standard

    var disallowRules: Map<String, MutableSet<String>> = mapOf()
        private set

    // Do all the parsing in a single pass in here
    init {
        val rules: MutableMap<String, MutableSet<String>> = mutableMapOf()

        val responseList: List<String> = EntityUtils.toString(response.entity).lines()

        var userAgent: String = ""

        for (line in responseList) {
            val splitVal = line.split(":").map(String::trim)
            val key: String = splitVal[0].toLowerCase()
            val value: String = splitVal[1].trim()

            if (key == "user-agent") {
                userAgent = value
                continue
            }

            if (key == "disallow") {
                if (userAgent in rules)
                    rules[userAgent]!!.add(value)
                else
                    rules[userAgent] = mutableSetOf(value)

                continue
            }
        }

        disallowRules = rules
    }
}

