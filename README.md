About
=====

Krawler is a web crawler written in Kotlin. It is heavily inspired by the
crawler4j (https://github.com/yasserg/crawler4j) by Yasser Ganjisaffar. Krawler
is maintained by Brian A. Madden in conjunction with http://www.thelandscape.io, and was
created to meet some of the specific needs of TheLandscape while also honing our Kotlin skills.

The project is still very new, and those looking for a mature, well tested crawler framework should
likely still use crawler4j. For those who can tolerate a bit of turbulence Krawler should serve as
a relatively drop-in replacement for crawler4j that provides some neat features and benefits such as:

* Kotlin native project!
* Krawler differentiates between a "check" and a "visit". 
Checks are used to verify the status code of a resource by issuing an HTTP HEAD request rather than a GET request.
Each policy (get or check) can have it's own logic associated with it by implementing 
either `shouldCheck` or `shouldVisit` and `check` and `visit`.
* Krawler's politeness delay is per-host rather than global. This way servers aren't overwhelmed, but crawls visiting
many hosts in parallel are not effectively serialized by the politeness delay.
* Krawler collects full anchor tags including all attributes and anchor text
* Krawler currently has no proxy support :(

Roadmap
=======
* Add support for collecting and respecting robots.txt
* Add option to prevent following redirects
* Proxy support

Random TODO
===========
1. If anchor tag specifies that URL is canonical use it as is
1. Clean up the requests class to properly close the connection pool
1. 