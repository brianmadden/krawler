package io.thelandscape.krawler.http

import com.google.common.net.InternetDomainName
import org.w3c.dom.Element
import java.net.URI
import org.w3c.dom.Node


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
 * TODO: Fix cases where relative URL results in domain info being lost
 *
 */

class KrawlUrl private constructor(private var url: String, parent: String) {

    companion object {
        fun new(url: String, parent: String = ""): KrawlUrl {
            return KrawlUrl(url, parent)
        }

        fun new(anchor: Element, parent: String = ""): KrawlUrl? {
            if (anchor.tagName != "a" && !anchor.hasAttribute("href"))
                return null
            return KrawlUrl(anchor, parent)
        }
    }

    // Make relative URLs absolute if we've got a parent URL
    init {
        val u: URI = URI(url)
        if (!u.isAbsolute) {
            val parentUri = URI(parent)
            url = parentUri.host + url
        }
    }

    // All of these setters will be private so that we can't set these from the outside
    var wasExtractedFromAnchor = false
        private set

    var anchorText: String? = null
        private set

    var anchorAttributes: Map<String, String>? = null
        private set

    // Constructor used when we pass a full anchor tag in
    private constructor(anchor: Element, parent: String): this(anchor.getAttribute("href"), parent) {

        wasExtractedFromAnchor = true
        // Anchor text is actually contained within the first child node
        anchorText = anchor.textContent ?: ""
        // Attributes are a map of Nodes where each Node is an Attribute
        // (https://docs.oracle.com/javase/8/docs/api/org/w3c/dom/Node.html)
        anchorAttributes = (0..anchor.attributes.length - 1).associate {
            anchor.attributes.item(it).nodeName to anchor.attributes.item(it).nodeValue
        }
    }

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
            if (normalForm.endsWith("/") || !normalForm.endsWith(suffix))
                normalForm
            else
                normalForm + "/"
        }

    val normalForm: String
        get() = uri.normalize().toASCIIString()

    val suffix: String
        // Get a list of TLDs from https://publicsuffix.org/list/public_suffix_list.dat
        get() = idn?.publicSuffix().toString()

    val domain: String
        get() = (uri.host ?: "")
                .replace("." + suffix, "")
                .split(".")
                .last() + "." + suffix

    val subdomain: String
        get() = (uri.host ?: "").replace("." + domain, "")

    val host: String = uri.host ?: ""

    val path: String = uri.path ?: ""

    override fun toString(): String = canonicalForm

}
