
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
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.util.EntityUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.charset.StandardCharsets

interface RequestResponse

data class ErrorResponse(val url: KrawlUrl, val reason: String = "An unknown error has occurred.") : RequestResponse
class KrawlDocument(val url: KrawlUrl, response: HttpResponse, context: HttpClientContext) : RequestResponse {

    constructor(url: KrawlUrl, response: HttpResponse, context: HttpClientContext,
                parent: KrawlUrl): this(url, response, context) {
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
    val headers: Map<String, String> = response.allHeaders.associate { it.name.toLowerCase() to it.value }

    /**
     * Raw HTML
     */
    val rawHtml: String = try { EntityUtils.toString(response.entity, StandardCharsets.UTF_8) ?: "" } catch (e: Throwable) { "" }

    /**
     * Status code
     */
    val statusCode: Int = response.statusLine.statusCode

    /**
     * Redirect history
     */
    val redirectHistory: List<RedirectHistoryNode> =
            context.getAttribute("fullRedirectHistory") as List<RedirectHistoryNode>? ?: listOf()

    /**
     * Document as parsed by JSoup
     */
    val parsedDocument: Document = Jsoup.parse(rawHtml)

    /**
     * Anchor tags that have the href attribute
     */
    val anchorTags: List<Element> = if (rawHtml.isNullOrEmpty()) listOf() else try {
        parsedDocument.getElementsByTag("a").toElementList().filter { it.hasAttr("href") }
    } catch (e: Throwable) {
        listOf<Element>()
    }

    /**
     * Other outgoing links (URLs from src tags of images, scripts, etc)
     */
    val otherOutgoingLinks: List<String> = parsedDocument
            .getElementsByAttribute("src")
            .toElementList()
            .map { it.attr("src") }


    /// Utility method to convert a NodeList to a List<Element>
    internal fun Elements.toElementList(): List<Element> {
        if (this.size == 0) return listOf()

        return (0..this.size - 1).map { this[it] }
    }
}


