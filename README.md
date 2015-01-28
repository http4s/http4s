# Http4s

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

Quick start from [giter8 template](http://github.com/http4s/http4s.g8).
