/**
 * Created by brian.a.madden@gmail.com on 10/21/16.
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

import io.thelandscape.krawler.http.KrawlUrl
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KrawlUrlTest {

    private val anchorNode: String = "<a href='http://www.google.com/./zxyzzy' " +
            "rel='canonical' target='blank'>Anchor Text</a>"
    private val dbf = DocumentBuilderFactory.newInstance()
    private val db = dbf.newDocumentBuilder()
    private val doc: Element = db.parse(ByteArrayInputStream(anchorNode.toByteArray())).documentElement

    val rawUrl = "http://www.xyz.abc.com/./zyxzzy"
    val testUrl = KrawlUrl.new(rawUrl)
    val anchorTestUrl = KrawlUrl.new(doc)

    @Test fun testRawURL() = assertEquals(rawUrl, testUrl.rawUrl)

    @Test fun testCanonicalForm() {
        // it should have a canonical form that is normalized
        assertEquals("http://www.xyz.abc.com/zyxzzy", testUrl.canonicalForm)

        // It should have a canonical form that adds a slash if the URL ends with the domain suffix
        val testAddSlash = KrawlUrl.new("http://www.xyz.com")
        assertEquals("http://www.xyz.com/", testAddSlash.canonicalForm)

        // It should have a canonical form that does not add a slash if it is already present
        val testNoAddDoubleSlash = KrawlUrl.new("http://www.xyz.com/")
        assertEquals("http://www.xyz.com/", testNoAddDoubleSlash.canonicalForm)

        // It should have a canonical form that does not add a slash if the URL does not end with the domain suffix
        val testNoAddSlash = KrawlUrl.new("http://www.xyz.com/index.html")
        assertEquals("http://www.xyz.com/index.html", testNoAddSlash.canonicalForm)
    }

    // it should have no /./ in normalized form
    @Test fun testNormalForm() = assertFalse(testUrl.normalForm.contains("///.//"))

    @Test fun testSuffix() = assertEquals("com", testUrl.suffix)

    @Test fun testDomain() = assertEquals("abc.com", testUrl.domain)

    @Test fun testSubdomain() = assertEquals("www.xyz", testUrl.subdomain)

    @Test fun testPath() = assertEquals("/./zyxzzy", testUrl.path)

    @Test fun testHost() = assertEquals("www.xyz.abc.com", testUrl.host)

    @Test fun testExtractedFromAnchor() {
        // The test URL was from a string
        assertFalse(testUrl.wasExtractedFromAnchor)
        // The anchorTestUrl was from an anchor tag

        // It was extracted from an anchor
        assertTrue(anchorTestUrl!!.wasExtractedFromAnchor)
        // It should have text associated with the anchor text
        assertEquals("Anchor Text", anchorTestUrl.anchorText)
        // It should have attributes
        val expectedAttrs = mapOf("href" to "http://www.google.com/./zxyzzy", "rel" to "canonical", "target" to "blank")
        assertEquals(expectedAttrs, anchorTestUrl.anchorAttributes)
    }

}