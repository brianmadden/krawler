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
 */

class KrawlUrl private constructor(url: String, parent: KrawlUrl?) {

    companion object {
        fun new(url: String, parent: KrawlUrl? = null): KrawlUrl {
            return KrawlUrl(url, parent)
        }

        fun new(anchor: Element, parent: KrawlUrl? = null): KrawlUrl? {
            if (anchor.tagName != "a" && !anchor.hasAttribute("href"))
                return null
            return KrawlUrl(anchor, parent)
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
    private constructor(anchor: Element, parent: KrawlUrl?): this(anchor.getAttribute("href"), parent) {

        wasExtractedFromAnchor = true
        // Anchor text is actually contained within the first child node
        anchorText = anchor.textContent ?: ""
        // Attributes are a map of Nodes where each Node is an Attribute
        // (https://docs.oracle.com/javase/8/docs/api/org/w3c/dom/Node.html)
        anchorAttributes = (0..anchor.attributes.length - 1).associate {
            anchor.attributes.item(it).nodeName to anchor.attributes.item(it).nodeValue
        }
    }

    val rawUrl: String = url

    // http://www.ietf.org/rfc/rfc2396.txt
    // URI syntax: [scheme:]scheme-specific-part[#fragment]

    // Absolute URLs start with a / immediately after the scheme portion
    val isAbsolute: Boolean = url.split(":").getOrElse(1, {""}).startsWith("/")

    // For our purposes all URLs are http(s) so if it isn't absolute assume http
    val scheme: String = if(isAbsolute) url.split("://").first() else "http"

    // The sanitized URL is trimmed and converted to absolute if it's relative w/ a parent
    val sanitizedUrl: String = if (isAbsolute) url.trim() else "${parent?.scheme}://${parent?.host}/${url.trim()}"

    // Host is the part between the :// and the first /
    val host: String = sanitizedUrl.split("://").getOrElse(1, {""}).split("/").first()

    // The path is the part after the host (starting with the first / after the host)
    val path: String = sanitizedUrl.split("://").getOrElse(1, {""}).removePrefix(host)

    // The normal form doesn't contain any /./ or /../
    val normalForm: String
        get() {
            // Make the scheme and host lowercase
            val lower = scheme.toLowerCase() + "://" + host.toLowerCase() + "/" + path
            // Remove default ports
            val port_removed = if (scheme == "http") lower.replace(":80", "") else
                if (scheme == "https") lower.replace(":443", "")
                else lower

            // Capitalize letters in % encoded triplets
            val capitalizedTriplets: String = port_removed
                    .split("%")
                    .mapIndexed { i, s -> if (i == 0) s else
                        s.mapIndexed { i, c -> if (i < 2) c.toUpperCase() else c }.joinToString("") }
                    .joinToString("%")

            // Decode % encoded triplets of unreserved characters
            val decodedTriplets: String = capitalizedTriplets

            // Remove /../
            val removedDots: String = decodedTriplets

            return removedDots
        }

    val canonicalForm: String = TODO()

    private val idn: InternetDomainName? = try {
        InternetDomainName.from(this.host)
    } catch (e: NullPointerException) {
        null
    }

    // Get a list of TLDs from https://publicsuffix.org/list/public_suffix_list.dat
    val suffix: String = idn?.publicSuffix().toString()

    val domain: String = host.replace("." + suffix, "").split(".").last() + "." + suffix

    val subdomain: String = host.replace("." + domain, "")

    override fun toString(): String = canonicalForm

}
