[![Release](https://jitpack.io/v/brianmadden/krawler.svg)](https://jitpack.io/#brianmadden/krawler) 
[![Build Status](https://travis-ci.org/brianmadden/krawler.svg?branch=master)](https://travis-ci.org/brianmadden/krawler)
[![Awesome Kotlin Badge](https://kotlin.link/awesome-kotlin.svg)](https://github.com/KotlinBy/awesome-kotlin)

About
=====

Krawler is a web crawling framework written in Kotlin. It is heavily inspired by
[crawler4j](https://github.com/yasserg/crawler4j) by Yasser Ganjisaffar. The project 
is still very new, and those looking for a mature, well tested crawler framework should
likely still use crawler4j. For those who can tolerate a bit of turbulence, Krawler should serve as
a replacement for crawler4j with minimal modifications to existing applications.
 
Some neat features and benefits of Krawler include:

* Kotlin project!
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

Add Dependency
======
Krawler is published through jitpack.io at: https://jitpack.io/#brianmadden/krawler/ . 
Add jitpack.io as a repository, and krawler as a dependency to use Krawler in your project:

#### Using Gradle
```gradle
repositories {
    jcenter()
    maven { url "https://jitpack.io" }
}

dependencies {
    compile 'com.github.brianmadden:krawler:0.4.4'
}
```
#### Using Gradle Kotlin Script
```gradle
repositories {
    jcenter()
    maven("https://jitpack.io")
}
dependencies {
    implementation("com.github.brianmadden:krawler:0.4.4
}
```

#### Using Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.brianmadden</groupId>
    <artifactId>krawler</artifactId>
    <version>0.4.4</version>
</dependency>
```

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

    private val FILTERS: Regex = Regex(".*(\\.(css|js|bmp|gif|jpe?g|png|tiff?|mid|mp2|mp3|mp4|wav|avi|" +
            "mov|mpeg|ram|m4v|pdf|rm|smil|wmv|swf|wma|zip|rar|gz|tar|ico))$", RegexOption.IGNORE_CASE)

    /**
     * Threadsafe whitelist of acceptable hosts to visit
     */
    val whitelist: MutableSet<String> = ConcurrentSkipListSet()

    override fun shouldVisit(url: KrawlUrl): Boolean {
        val withoutGetParams: String = url.canonicalForm.split("?").first()
        return (!FILTERS.matches(withoutGetParams) && url.host in whitelist)
    }

    private val counter: AtomicInteger = AtomicInteger(0)

    override fun visit(url: KrawlUrl, doc: KrawlDocument) {
        println("${counter.incrementAndGet()}. Crawling ${url.canonicalForm}")
    }

    override fun onContentFetchError(url: KrawlUrl, reason: String) {
        println("${counter.incrementAndGet()}. Tried to crawl ${url.canonicalForm} but failed to read the content.")
    }

    private var startTimestamp: Long = 0
    private var endTimestamp: Long = 0

    override fun onCrawlStart() {
        startTimestamp = LocalTime.now().toNanoOfDay()
    }
    override fun onCrawlEnd() {
        endTimestamp = LocalTime.now().toNanoOfDay()
        println("Crawled $counter pages in ${(endTimestamp - startTimestamp) / 1000000000.0} seconds.")
    }
}
```

Roadmap
=======
* Proxy support
* Headless Chrome support for crawling Javascript driven sites

Release Notes
=============
**0.4.4 (2020-1-29)**
- Upgrade Kotlin to 1.3.61
- Upgrade `kotlinx.coroutines`. This required an update to some of the places where coroutine builders were called internally.
- Upgrade Gradle wrapper

**0.4.3 (2017-11-20)**
- Added ability to clear crawl queues by RequestId and Age, see `Krawler#removeUrlsByRootPage` 
  and `Krawler#removeUrlsByAge`
- Added config option to prevent crawler shutdown on empty queues
- Added new single byte priority field to `KrawlQueueEntry`. Queues will always attempt to pop the `lowest` priority
  entry available. Priority can be assigned by overriding the `Krawler#assignQueuePriorty` method.
- Update dependencies

**0.4.2 (2017-10-25)**
- Updated to Kotlin Runtime 1.1.51, kotlinx-coroutines 0.19.2
- Reworked KrawlUrl class internals to handle spaces in URLs better which should result in
more stability when crawling.

**0.4.1 (2017-8-15)**
- Removed logging implementation from dependencies to prevent logging conflicts when used as a library.
- Updated Kotlin version to 1.1.4
- Updated `kotlinx.coroutines` to .17

**0.4.0 (2017-5-17)**
- Rewrote core crawl loop to use Kotlin 1.1 coroutines. This has effectively turned the crawl process into a
multi-stage pipeline. This architecture change has removed the necessity for some locking by removing resource 
contention by multiple threads.

- Updated the build file to build the simple example as a runnable jar
 
- Minor bug fixes in the KrawlUrl class.

**0.3.2 (2017-3-3)**
- Fixed a number of bugs that would result in a crashed thread, and subsequently an incorrect number of crawled pages
as well as cause slowdowns due to a reduced number of worker threads.

- Added a new utility function to wrap `doCrawl` and log any uncaught exceptions during crawling. 

**0.3.1 (2017-2-2)**
- Created 1:1 mapping between threads and the number of queues used to serve URLs to visit. URLs have an
affinity for a particular queue based on their domain. All URLs from that domain will end up in the same
queue. This improves parallel crawl performance by reducing the frequency that the politeness delay
effects requests. For crawls bound to fewer domains than queues, the excess queues are not used.

- Many bug fixes including fix that eliminates accidental over-crawling.

**0.2.2 (2017-1-21)**
- Added additional configuration option for redirect handling in KrawlConfig. Setting
`useFastRedirectHandling = true` (when redirects are enabled) will cause Krawler to 
automatically follow redirects, keeping a history of the transitions and status codes.
This history is present in the `KrawlDocument#redirectHistory` property.


**0.2.1 (2017-1-20)**
- Redirect handling has been changed. Redirects can be followed or not via configuration
option in `KrawlConfig`. When redirects are enabled the redirected to URL will be added 
to the queue as a part of the link harvesting phase of Krawler.

- If an anchor tag specifies `rel='canonical'` the `canonicalForm` will not be subject
to further processing.

- `KrawlUrl.new`'s implementation has been changed to prevent `null` from being returned
in certain circumstances.


**0.2.0 (2017-1-18)** 

- Krawler now respects robots.txt. This feature can be configured by passing a custom `RobotsConfig` 
to your `Krawler` instance. By default Krawler will respect robots.txt without any additional configuration.
- Krawler now collects outgoing links from `src` attributes of tags in addition to the `href` of anchor tags.
- Minor bug fixes and refactorings.
