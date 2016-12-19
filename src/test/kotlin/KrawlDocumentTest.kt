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

import io.thelandscape.krawler.http.KrawlDocument
import io.thelandscape.krawler.http.KrawlUrl
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import org.apache.http.message.BasicStatusLine
import org.junit.Test
import org.w3c.dom.NodeList
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun prepareResponse(expectedResponseCode: Int, responseBody: String): HttpResponse {
    val ret = BasicHttpResponse(
            BasicStatusLine(
                    ProtocolVersion("HTTP", 1, 1),
                    expectedResponseCode,
                    "")
    )
    ret.setStatusCode(expectedResponseCode)
    ret.entity = StringEntity(responseBody)

    return ret
}

private val mockReturn = prepareResponse(200, "<html><head><title>ABC</title></head>" +
        "<body><a href='http://www.google.com' rel='canonical'>ABC LINK</a></body></html>")

private val mockEmptyReturn = prepareResponse(200, "")
private val mock404Return = prepareResponse(404, "<html><body>404 Not Found!</body></html>")

private val parent: KrawlUrl = KrawlUrl.new("http://www.parent.com/")

class CrawlDocumentTest {

    val doc: KrawlDocument = KrawlDocument(mockReturn)
    val emptyDoc: KrawlDocument = KrawlDocument(mockEmptyReturn, parent)
    val four04Doc: KrawlDocument = KrawlDocument(mock404Return)

    // All docs should have a headers property
    @Test fun testHeadersProperty() {
        // Headers should be a map
        assertTrue { doc.headers is Map<String, String> }
        assertTrue { emptyDoc.headers is Map<String, String> }
        assertTrue { four04Doc.headers is Map<String, String> }

        // Headers should be empty
        assertTrue { doc.headers.isEmpty() }
        assertTrue { emptyDoc.headers.isEmpty() }
        assertTrue { four04Doc.headers.isEmpty() }
    }

    @Test fun testRawHtmlProperty() {
        // it should have a rawHtml element with a title and one anchor tag
        assertEquals("<html><head><title>ABC</title></head>" +
                "<body><a href='http://www.google.com' rel='canonical'>ABC LINK</a></body></html>", doc.rawHtml)

        // Empty doc should be just the empty string
        assertEquals("", emptyDoc.rawHtml)

        // 404 doc should be the 404 message as HTML
        assertEquals("<html><body>404 Not Found!</body></html>", four04Doc.rawHtml)
    }

    @Test fun testAnchorTags() {
        // Doc should only have one anchor tag
        assertEquals(1, doc.anchorTags.size)
        // The href property should point to google
        assertEquals("http://www.google.com", doc.anchorTags.first().getAttribute("href"))

        // Empty doc should have no links
        assertTrue { emptyDoc.anchorTags.isEmpty() }
        // 404 doc should also have no anchor tags
        assertTrue { four04Doc.anchorTags.isEmpty() }
    }

    // it should have a status code of 200
    @Test fun testStatusCode() {
        assertEquals(200, doc.statusCode)
        assertEquals(404, four04Doc.statusCode)
        assertEquals(200, emptyDoc.statusCode)
    }

    @Test fun testParent() {
        assertNull(doc.parent)
        assertNull(four04Doc.parent)
        assertEquals(parent, emptyDoc.parent)
    }
}
