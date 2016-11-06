package io.thelandscape.krawler.http

import java.net.URI
import java.net.URL

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

class KrawlUrl(url: String) {

    val rawUrl: String = url
    private val uri: URI = URI(rawUrl)
    private val url: URL = uri.toURL()

    // It is HTTP if it is -NOT- opaque and
    val isHttp: Boolean = !uri.isOpaque &&
            // it is not absolute OR it is absolute and it's scheme is http or https
            (!uri.isAbsolute || (uri.isAbsolute && (uri.scheme != "http" || uri.scheme != "https")))

    val canonicalForm: String
        get() = if (uri.isOpaque) normalForm else {
            if (normalForm.endsWith("/"))
                normalForm
            else
                normalForm + "/"
        }

    val normalForm: String
        get() = uri.normalize().toASCIIString()

    val domain: String
        get() {
            val x = uri.toURL()
            val host: String = uri.host
            return x.host
        }

    override fun toString(): String = canonicalForm

}