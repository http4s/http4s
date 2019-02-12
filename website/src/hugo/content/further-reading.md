---
menu: main
weight: 500
title: Further reading
---

## Blogs and slides

These materials are from third parties.  Some of the material may be
dated, but is considered interesting enough to share as a supplement
to the official documentation.

* [Lightweight, functional microservices with http4s and doobie](https://www.youtube.com/watch?v=fQfMiUDsLv4) ([slides](https://kubukoz.github.io/talks/http4s-doobie-micro/slides/)) <small class="text-muted">(2019-01-25)</small> 
* [HTTP applications are just a Kleisli function from a streaming request to a polymorphic effect of a streaming response. So what's the problem?](https://www.youtube.com/watch?v=urdtmx4h5LE) ([slides](https://rossabaker.github.io/boston-http4s/#2)) <small class="text-muted">(2018-03-20)</small> 
* [Testing and Error Handling in http4s](https://medium.com/@albamus/testing-and-error-handling-in-http4s-2a05572e535d) <small class="text-muted">(2017-08-26)</small>
* [Combining data from a database and a web service with Fetch](https://www.47deg.com/blog/fetch-doobie-http4s/) <small class="text-muted">(2017-01-19)</small>
* [CRUD and error handling with Http4s](https://partialflow.wordpress.com/2016/10/18/crud-and-error-handling-with-http4s/) <small class="text-muted">(2016-10-18)</small>
* [Http4s, Doobie and Circe: The Functional Web Stack](https://www.slideshare.net/GaryCoady/http4s-doobie-and-circe-the-functional-web-stack) <small class="text-muted">(2016-04-15)</small>
* [HttpRequest and pattern matching on requests](http://www.lyranthe.org/http4s/2016/02/18/request-pattern-matching.html) <small class="text-muted">(2016-02-18)</small>
* [Seting up a http4s skeleton project](http://www.lyranthe.org/http4s/2016/02/16/setting-up-http4s.html) <small class="text-muted">(2016-02-16)</small>
* [Streaming of data using http4s and scalaz-stream](http://immutables.pl/2016/01/16/Streaming-data-using-http4s-and-scalaz-stream/) <small class="text-muted">(2016-01-16)</small>

## Dependencies

http4s stands on the shoulders of giants.  To use it most effectively,
it helps to be familiar with its dependencies.

* [Cats](https://typelevel.github.io/cats) provides typeclasses and a
  few core data types.
  * [Resources for Learners](https://typelevel.org/cats/resources_for_learners.html)
* [cats-effect](https://typelevel.github.io/cats-effect) is used to
  mark effectful code.
  * [An IO monad for cats](https://typelevel.org/blog/2017/05/02/io-monad-for-cats.html)
* [FS2](https://functional-streams-for-scala.github.io/fs2/) provides
  support for streaming.
  * [The Official Guide](https://github.com/functional-streams-for-scala/fs2/blob/series/0.10/docs/guide.md)
* [Circe](https://circe.github.io/circe/) is the recommended library for JSON.
