
/**
 * Created by brian.a.madden@gmail.com on 10/26/16.
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

package io.thelandscape.krawler.http

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

interface RequestResponse

data class ErrorResponse(val url: KrawlUrl) : RequestResponse
class KrawlDocument(val url: KrawlUrl, response: HttpResponse) : RequestResponse {

    constructor(url: KrawlUrl, response: HttpResponse, parent: KrawlUrl): this(url, response) {
        this.parent = parent
    }

    /**
     * URL of the parent of this page
     */
    var parent: KrawlUrl? = null
        private set

    /**
     * Http headers
     */
    val headers: Map<String, String> = response.allHeaders.associate { it.name to it.value }

    /**
     * Raw HTML
     */
    val rawHtml: String = try { EntityUtils.toString(response.entity) ?: "" } catch (e: Throwable) { "" }

    /**
     * Status code
     */
    val statusCode: Int = response.statusLine.statusCode

    /**
     * Anchor tags that have the href attribute
     */
    val anchorTags: List<Element> = if (rawHtml.isNullOrEmpty()) listOf() else try {
        val doc: Document = Jsoup.parse(rawHtml)
        doc.getElementsByTag("a").toElementList().filter { it.hasAttr("href") }
    } catch (e: Throwable) {
        listOf<Element>()
    }

    /// Utility method to convert a NodeList to a List<Element>
    internal fun Elements.toElementList(): List<Element> {
        if (this.size == 0) return listOf()

        return (0..this.size - 1).map { this[it] }
    }
}

