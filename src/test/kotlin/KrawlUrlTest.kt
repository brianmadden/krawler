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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KrawlUrlTest {

    private val anchorNode: String = "<a href='http://www.google.com/./zxyzzy' " +
            "rel='canonical' target='blank'>Anchor Text</a>"
    private val doc: Element = Jsoup.parse(anchorNode).getElementsByTag("a").first()

    val rawUrl = "HTTP://www.xyz.ABC.com:80/../%7Ezyxzzy/./abc%3a"
    val testUrl = KrawlUrl.new(rawUrl)
    val anchorTestUrl = KrawlUrl.new(doc)

    @Test fun testHierarchicalPart() {
        assertEquals("http://www.xyz.abc.com", testUrl.hierarchicalPart)
    }

    @Test fun testCleansExcessiveSchemeSlashes() {
        val tester = KrawlUrl.new("http:////////testexample.com")
        assertEquals("http://testexample.com/", tester.canonicalForm)
    }

    // Test the case where scheme is to be inferred from the parent by starting the URL with //
    @Test fun testInferScheme() {
        val httpsUrl = KrawlUrl.new("https://somesafesite.com")
        val url = KrawlUrl.new("//something.org", httpsUrl)
        assertEquals("https", url.scheme)

    }

    // Ensure that we don't accidentally double slash
    // Also ensure that a colon in a relative URL doesn't get identified as
    // a scheme if the relative URL doesn't start with a slash
    @Test fun testParentHostDoesntCauseDoubleSlashes() {
        val parent = KrawlUrl.new("http://www.example.org/")
        val url = KrawlUrl.new("foo:bar:bas", parent)
        assertEquals("http://www.example.org/foo:bar:bas", url.canonicalForm)
    }

    @Test fun testPort() = assertEquals(testUrl.port, 80)

    @Test fun testRelativeUrlWithTwoColons() {
        val url = KrawlUrl.new("/wiki/foo:bar:bas")
        assertEquals("http", url.scheme)
        assertEquals("/wiki/foo:bar:bas", url.path)
    }

    @Test fun testAbsoluteWithTwoColonsAndNoPort() {
        val url = KrawlUrl.new("http://www.example.org/foo:bar:bas")
        assertEquals("http", url.scheme)
        assertEquals(80, url.port)
        assertEquals("/foo:bar:bas", url.path)
    }

    @Test fun testUrlWithNoPath() {
        val noPath = KrawlUrl.new("http://www.example.org")
        assertEquals("www.example.org", noPath.host)
        assertEquals("example.org", noPath.domain)
        assertEquals("www", noPath.subdomain)
        assertEquals("/", noPath.path)
    }

    @Test fun testUrlWithNoHost() {
        val noHost = KrawlUrl.new("/relative/path", testUrl)
        val noHost2 = KrawlUrl.new("relative/path", testUrl)
        val relWithColon = KrawlUrl.new("some/directory/Something:SomethingElse", testUrl)

        assertEquals("http://www.xyz.abc.com/relative/path", noHost.canonicalForm)
        assertEquals("www.xyz.abc.com", noHost.host)
        assertEquals("abc.com", noHost.domain)
        assertEquals("/relative/path", noHost.path)

        // Verify everything works without a leading / as well
        assertEquals("http://www.xyz.abc.com/relative/path", noHost2.canonicalForm)
        assertEquals("www.xyz.abc.com", noHost2.host)
        assertEquals("abc.com", noHost2.domain)
        assertEquals("/relative/path", noHost2.path)

        // Verify everything works despite the colon and no scheme
        assertEquals("http", relWithColon.scheme)
        assertEquals("www.xyz.abc.com", relWithColon.host)
        assertEquals("/some/directory/Something:SomethingElse", relWithColon.path)
    }

    @Test fun testRawURL() = assertEquals(rawUrl, testUrl.rawUrl)

    @Test fun testCanonicalForm() {
        // it should have a canonical form that is normalized
        assertEquals("http://www.xyz.abc.com/~zyxzzy/abc%3A", testUrl.canonicalForm)

        // Anchor test url should be unchanged since rel=canonical
        assertEquals("http://www.google.com/./zxyzzy", anchorTestUrl!!.canonicalForm)
    }

    // it should have no /./ in normalized form
    @Test fun testNormalForm() {
        // General equality test
        assertEquals("http://www.xyz.abc.com/~zyxzzy/abc%3A", testUrl.normalForm)
        // Ensure lower case scheme and host
        assertEquals(testUrl.scheme.toLowerCase(), testUrl.scheme)
        assertEquals(testUrl.host.toLowerCase(), testUrl.host)
        // Ensure capitalized encoded octets
        // Make sure we've removed the /../
        assertFalse(testUrl.normalForm.contains("///..//"))
    }

    @Test fun testScheme() = assertEquals("http", testUrl.scheme)

    @Test fun testSuffix() = assertEquals("com", testUrl.suffix)

    @Test fun testDomain() = assertEquals("abc.com", testUrl.domain)

    @Test fun testSubdomain() = assertEquals("www.xyz", testUrl.subdomain)

    @Test fun testPath() = assertEquals("/~zyxzzy/abc%3A", testUrl.path)

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