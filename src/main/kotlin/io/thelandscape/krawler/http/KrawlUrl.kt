package io.thelandscape.krawler.http

import java.net.URI
import com.google.common.net.InternetDomainName

/**
 * Created by @brianmadden on 10/21/16.
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

/**
 * Class to represent a URL that was either provided as a seed, or found during crawl.
 * If the URL was found during crawl, it will contain additional information such as
 * the anchor text, rel property, and any other pertinent data, otherwise these fields
 * will be blank.
 *
 */
class KrawlUrl(url: String) {

    private val uri: URI = URI(url)
    private val idn: InternetDomainName? = try {
        InternetDomainName.from(uri.host)
    } catch (e: NullPointerException) {
        null
    }

    val rawUrl: String = url

    // It is HTTP if it is -NOT- opaque and
    val isHttp: Boolean = !uri.isOpaque &&
            // it is not absolute OR it is absolute and it's scheme is http or https
            (!uri.isAbsolute || (uri.isAbsolute && (uri.scheme == "http" || uri.scheme == "https")))

    val canonicalForm: String
        get() = if (uri.isOpaque) normalForm else {
            if (normalForm.endsWith("/"))
                normalForm
            else
                normalForm + "/"
        }

    val normalForm: String
        get() = uri.normalize().toASCIIString()

    val wasExtractedFromAnchor: Boolean = false

    // TODO: Find just the TLD suffix
    // Get a list from https://publicsuffix.org/list/public_suffix_list.dat
    val suffix: String
        get() = idn?.publicSuffix().toString()


    val domain: String
        get() = uri.host
                .replace("." + suffix, "")
                .split(".")
                .last()

    val subdomain: String
        get() = uri.host
                .replace("." + suffix, "")
                .replace("." + domain, "")

    // TODO: Find path
    val path: String = ""


    override fun toString(): String = canonicalForm

}
