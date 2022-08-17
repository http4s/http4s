
# Further Reading


## Blogs and Slides

These materials are from third parties.  Some of the material may be
dated, but is considered interesting enough to share as a supplement
to the official documentation.

* [Live Coding a Chat Server with WebSockets and http4s](https://www.youtube.com/watch?v=py_V_7gD5WU) ([code](https://github.com/MartinSnyder/phase-http4s)) (2019-04-30)
* [Lightweight, functional microservices with http4s and doobie](https://www.youtube.com/watch?v=fQfMiUDsLv4) ([slides](https://kubukoz.github.io/talks/http4s-doobie-micro/slides/)) (2019-01-25)
* [Error handling in Http4s with classy optics (Part 2)](https://typelevel.org/blog/2018/11/28/http4s-error-handling-mtl-2.html) (2018-11-28)
* [Error handling in Http4s with classy optics (Talk)](https://www.youtube.com/watch?v=UUX5KvPgejM) (2018-11-16)
* [Error handling in Http4s with classy optics (Part 1)](https://typelevel.org/blog/2018/08/25/http4s-error-handling-mtl.html) (2018-08-25)
* [HTTP applications are just a Kleisli function from a streaming request to a polymorphic effect of a streaming response. So what's the problem?](https://www.youtube.com/watch?v=urdtmx4h5LE) ([slides](https://rossabaker.github.io/boston-http4s/#2)) (2018-03-20)
* [Testing and Error Handling in http4s](https://medium.com/@albamus/testing-and-error-handling-in-http4s-2a05572e535d) (2017-08-26)
* [Combining data from a database and a web service with Fetch](https://www.47deg.com/blog/fetch-doobie-http4s/) (2017-01-19)
* [CRUD and error handling with Http4s](https://partialflow.wordpress.com/2016/10/18/crud-and-error-handling-with-http4s/) (2016-10-18)
* [Http4s, Doobie and Circe: The Functional Web Stack](https://www.slideshare.net/GaryCoady/http4s-doobie-and-circe-the-functional-web-stack) (2016-04-15)
* [HttpRequest and pattern matching on requests](http://www.lyranthe.org/http4s/2016/02/18/request-pattern-matching.html) (2016-02-18)
* [Seting up a http4s skeleton project](http://www.lyranthe.org/http4s/2016/02/16/setting-up-http4s.html) (2016-02-16)
* [Streaming of data using http4s and scalaz-stream](http://immutables.pl/2016/01/16/Streaming-data-using-http4s-and-scalaz-stream/) (2016-01-16)

## Dependencies

http4s stands on the shoulders of giants.  To use it most effectively,
it helps to be familiar with its dependencies.

* [Cats](https://typelevel.github.io/cats) provides typeclasses and a
  few core data types.
  * [Resources for Learners](https://typelevel.org/cats/resources_for_learners.html)
* [cats-effect](https://typelevel.github.io/cats-effect) is used to
  mark effectful code.
  * [An IO monad for cats](https://typelevel.org/blog/2017/05/02/io-monad-for-cats.html)
* [FS2](https://fs2.io) provides
  support for streaming.
  * [The Official Guide](https://fs2.io/#/guide)
* [Circe](https://circe.github.io/circe/) is the recommended library for JSON.
