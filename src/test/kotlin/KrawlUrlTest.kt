import io.thelandscape.krawler.http.KrawlUrl
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

class KrawlUrlTest {

    val testUrl = KrawlUrl("http://www.abc.com/./")

    @Test fun testIsHttp() {
        // Verify that an absolute URL with http & https isHttp returns true
        val absoluteHttp = KrawlUrl("http://www.abc.com")
        val absoluteHttps = KrawlUrl("https://www.abc.com")
        assertTrue { absoluteHttp.isHttp && absoluteHttps.isHttp }

        // Verify that absolute URLs with a scheme other than http isHttp returns false
        val absoluteNonHttp = KrawlUrl("file://abc/def")
        assertFalse { absoluteNonHttp.isHttp }

        // Verify that an opaque URI isHttp returns false
        val opaqueUri = KrawlUrl("mailto:abc@abc.com")
        assertFalse { opaqueUri.isHttp }
    }

    // it should have a canonical and normal form that are equal
    @Test fun normalFormIsCanonicalForm() {
        assertEquals(testUrl.canonicalForm, testUrl.normalForm)
    }

    @Test fun testNormalForm() {
        // it should have no /./ in normalized form
        assertFalse(testUrl.normalForm.contains("///.//"))
    }

    @Test fun testDomain() {
        assertEquals("abc.com", testUrl.domain)
    }
}