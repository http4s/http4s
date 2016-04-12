# Http4s [![Build Status](https://travis-ci.org/http4s/http4s.svg?branch=master)](https://travis-ci.org/http4s/http4s) [![Gitter chat](https://badges.gitter.im/http4s/http4s.png)](https://gitter.im/http4s/http4s) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.http4s/http4s-core_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.http4s/http4s-core_2.11)

Http4s is a minimal, idiomatic Scala interface for HTTP services.  Http4s is
Scala's answer to Ruby's Rack, Python's WSGI, Haskell's WAI, and Java's
Servlets.

```scala
val service = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello, better world.")
  }
```

Learn more at [http4s.org](http://http4s.org/).

http4s is proud to be a [Typelevel](http://typelevel.org/) incubator
project.  We are dedicated to providing a harassment-free community
for everyone, and ask that the community adhere to the
[code of conduct](http://typelevel.org/conduct.html).
