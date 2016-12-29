About
=====

Krawler is a web crawler written in Kotlin. It is heavily inspired by the
Crawler4j (https://github.com/yasserg/crawler4j) by Yasser Ganjisaffar.
Major differences between Krawler and crawler4j are:

* Krawler differentiates between a "check" and a "visit". Checks are used to verify the status code of a resource by issuing an HTTP HEAD request rather than a GET request.
* Full redirect chain history can be constructed when a page is visited, not just when determining if a page should be visited.
* Krawler collects full anchor tags including all attributes
* Krawler currently has no proxy support


Roadmap
=======
* Clean up Requests class to properly close the HTTP request pool
* Add options for collecting and respecting robots.txt
* Add option to prevent following redirects
* Proxy support