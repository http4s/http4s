---
menu: main
title: CSRF
weight: 123
---

Http4s provides [Middleware], named `CSRF`, to avoid Cross-site request forgery attacks.This middleware 
is modeled after the [double submit cookie pattern][https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html]: 

Examples in this document have the following dependencies.

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion
)
```

And we need some imports.

```tut:silent
  import cats.effect._
  import org.http4s._
  import org.http4s.dsl.io._
  import org.http4s.implicits._
  import org.http4s.headers.Referer
  import org.http4s.server.middleware._
```

Let's start by making a simple service.

```tut:book
val service = HttpRoutes.of[IO] {
  case _ =>
    Ok()
} 

val request = Request[IO](Method.GET, uri"/")

service.orNotFound(request).unsafeRunSync
```

That didn't do all that much. Lets build out our CSRF Middleware by creating a `CSRFBuilder`

```tut:silent
val cookieName = "csrf-token"
val key  = CSRF.generateSigningKey[IO].unsafeRunSync
val defaultOriginCheck: Request[IO] => Boolean =
  CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, None)
val csrfBuilder = CSRF[IO,IO](key, defaultOriginCheck)
```

More info on what is possible [here](https://http4s.org/v0.21/api/org/http4s/server/middleware/csrf$$csrfbuilder),
but we will create for a fairly simple CSRF Middleware in our example.

```tut:book
val csrf = csrfBuilder.withCookieName(cookieName).withCookieDomain(Some("localhost")).withCookiePath(Some("/")).build
```

Now we need to wrap this around our service!
```tut:book
val dummyRequest: Request[IO] =
    Request[IO](method = Method.GET).putHeaders(Header("Origin", "http://localhost"))
val dummyPostRequest: Request[IO] =
    Request[IO](method = Method.POST).putHeaders(Header("Origin", "http://localhost"))
val resp = csrf.validate()(service.orNotFound)(dummyRequest).unsafeRunSync()
val resp = csrf.validate()(service.orNotFound)(dummyPostRequest).unsafeRunSync()
```

Notice how the response has the CSRF cookies added. How easy was
that? And, as described in [Middleware], services and middleware can be
composed such that only some of your endpoints are CSRF enabled. By default, 
safe methods will update the CSRF token, while unsafe methods will validate them.

Without getting too deep into it, safe methods are OPTIONS,GET,and HEAD. While unsafe methods are 
POST, PUT, PATCH, DELETE and TRACE. To put it simply, state changing methods are unsafe. For more information,
check out this cheat sheet on the the [double submit cookie pattern][https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html]


[Middleware]: ../middleware
