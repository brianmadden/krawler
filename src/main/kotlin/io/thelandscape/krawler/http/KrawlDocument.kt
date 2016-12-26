package io.thelandscape.krawler.http

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

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

// Only need these once for all documents
private val dbf = DocumentBuilderFactory.newInstance()
private val db = dbf.newDocumentBuilder()

interface RequestResponse

class ErrorResponse() : RequestResponse
class KrawlDocument(private val response: HttpResponse) : RequestResponse {

    constructor(response: HttpResponse, parent: KrawlUrl): this(response) {
        this.parent = parent
    }

    /**
     * URL of the parent of this page
     */
    var parent: KrawlUrl? = null
        get() = field
        private set(value) { field = value }
    /**
     * Http headers
     */
    val headers: Map<String, String>
        get() = response.allHeaders.associate { it.name to it.value }

    /**
     * Raw HTML
     */
    val rawHtml: String = try { EntityUtils.toString(response.entity) ?: "" } catch (e: Throwable) { "" }

    /**
     * Status code
     */
    val statusCode: Int
        get() = response.statusLine.statusCode

    /**
     * Anchor tags that have the href attribute
     */
    val anchorTags: List<Element>
        get() {
            val parsed: Document = try {
                val ba: ByteArrayInputStream = ByteArrayInputStream(rawHtml.toByteArray())
                db.parse(ba)
            } catch (e: Throwable) {
                return listOf()
            }

            return parsed.getElementsByTagName("a").toElementList().filter { it.hasAttribute("href") }
        }

    /// Utility method to convert a NodeList to a List<Element>
    internal fun NodeList.toElementList(): List<Element> {
        if (this.length == 0) return listOf()

        return (0..this.length - 1)
                .map { this.item(it) }
                .map { it as Element }
    }
}

