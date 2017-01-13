About
=====

Krawler is a web crawling framework written in Kotlin. It is heavily inspired by the
crawler4j (https://github.com/yasserg/crawler4j) by Yasser Ganjisaffar. Krawler
is maintained by Brian A. Madden in conjunction with [TheLandscape](http://www.thelandscape.io), and was
created to meet the specific needs of TheLandscape while also honing our Kotlin skills.

The project is still very new, and those looking for a mature, well tested crawler framework should
likely still use crawler4j. For those who can tolerate a bit of turbulence, Krawler should serve as
a replacement for crawler4j with minimal modifications to exisiting applications.
 
Some neat features and benefits of Krawler include:

* Kotlin native project!
* Krawler differentiates between a "check" and a "visit". 
Checks are used to verify the status code of a resource by issuing an HTTP HEAD request rather than a GET request.
Each policy (get or check) can have it's own logic associated with it by implementing 
either `shouldCheck` or `shouldVisit` and `check` and `visit`.
* Krawler's politeness delay is per-host rather than global. This way servers aren't overwhelmed, but crawls visiting
many hosts in parallel are not effectively serialized by the politeness delay.
* Krawler uses Jsoup for parsing HTML files while harvesting links, making it more tolerant of malformed or 
poorly written websites, and thus less likely to error out during a crawl. The original HTML of the page is
still available to facilitate validation and checking though.
* Krawler collects full anchor tags including all attributes and anchor text.
* Krawler currently has no proxy support, but it is on the roadmap. :(

Usage
=====
Using the Krawler framework is fairly simple. Minimally, there are two methods that must be overridden
in order to use the framework. Overriding the `shouldVisit` method dictates what should be visited by
the crawler, and the `visit` method dictates what happens once the page is visited. Overriding these
two methods is sufficient for creating your own crawler, however there are additional methods that
can be overridden to privde more robust behavior.

The full code for this simple example can also be found in the [example project](...):
```kotlin
class SimpleExample(config: KrawlConfig = KrawlConfig()) : Krawler(config) {

    private val pagesCrawled: MutableList<String> = mutableListOf()

    private val FILTERS: Regex = Regex(".*(\\.(css|js|bmp|gif|jpe?g|png|tiff?|mid|mp2|mp3|mp4|wav|avi|" +
            "mov|mpeg|ram|m4v|pdf|rm|smil|wmv|swf|wma|zip|rar|gz|tar|ico))$")

    override fun shouldVisit(url: KrawlUrl): Boolean {
        val withoutGetParams: String = url.canonicalForm.split("?").first()

        return (!FILTERS.matches(withoutGetParams) && url.host == "en.wikipedia.org")
    }

    override fun visit(url: KrawlUrl, doc: KrawlDocument) {
        println("${this.visitCount}. Crawling ${url.canonicalForm}")
    }

    override fun onCrawlEnd() {
        println("Crawled $visitCount pages.")
        pagesCrawled.forEach(::println)
    }
}
```

Roadmap
=======
* Add support for collecting and respecting robots.txt
* Proxy support
