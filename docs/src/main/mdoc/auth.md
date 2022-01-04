---
menu: main
weight: 120
title: Authentication
---

## Built in

A [service] is a `Kleisli[OptionT[F, ?], Request[F], Response[F]]`, the composable version of
`Request[F] => OptionT[F, Response[F]]`. A service with authentication also requires some kind of `User`
object which identifies which user did the request. To reference the `User` object
along with the `Request[F]`, there's `AuthedRequest[F, User]`, which is equivalent to
`(User, Request[F])`. So the service has the signature `AuthedRequest[F, User] =>
OptionT[F, Response[F]]`, or `AuthedService[User, F]`. So we'll need a `Request[F] => Option[User]`
function, or more likely, a `Request[F] => OptionT[F, User]`, because the `User` can
come from a database. We can convert that into an `AuthMiddleware` and apply it.
Or in code, using `cats.effect.IO` as the effect type:

```scala mdoc
import cats._, cats.effect._, cats.implicits._, cats.data._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server._

case class User(id: Long, name: String)

val authUser1: Kleisli[OptionT[IO, ?], Request[IO], User] = Kleisli(_ => OptionT.liftF(IO(???)))
val authMiddleware1: AuthMiddleware[IO, User] = AuthMiddleware(authUser1)
val authedService: AuthedService[User, IO] =
  AuthedService {
    case GET -> Root / "welcome" as user => Ok(s"Welcome, ${user.name}")
  }
val service1: HttpService[IO] = authMiddleware1(authedService)
```

## Returning an Error Response

Usually, it should also be possible to send back a 401 in case there was no
valid login. The 401 response can be adjusted as needed, some applications use a
redirect to a login page, or a popup requesting login data. With the upcoming of
[SPA], the correct http status codes are relevant again.

### With Kleisli

To allow for failure, the `authUser` function has to be adjusted to a `Request[F]
=> F[Either[String,User]]`. So we'll need to handle that possibility. For advanced
error handling, we recommend an error [ADT] instead of a `String`.

```scala mdoc
val authUser2: Kleisli[IO, Request[IO], Either[String,User]] = Kleisli(_ => IO(???))

val onFailure2: AuthedService[String, IO] = Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))
val authMiddleware2 = AuthMiddleware(authUser2, onFailure2)

val service2: HttpService[IO] = authMiddleware2(authedService)
```

## Implementing authUser

There's a few different ways to send authorization information with a HTTP
request, the two most common are cookie for regular browser usage or the
`Authorization` header for [SPA].

### Cookies

We'll use a small library for the signing/validation of the cookies, which
basically contains the code used by the Play framework for this specific task.

```scala
libraryDependencies += "org.reactormonk" %% "cryptobits" % "{{< version cryptobits >}}"
```

First, we'll need to set the cookie. For the crypto instance, we'll need to
provide a private key. You usually want to set a static secret so people don't
lose their session on server restarts, and a static secret also allows you to
use multiple application instances.

The message is simply the user id.

```scala mdoc
import org.reactormonk.{CryptoBits, PrivateKey}
import java.time._

val key = PrivateKey(scala.io.Codec.toUTF8(scala.util.Random.alphanumeric.take(20).mkString("")))
val crypto = CryptoBits(key)
val clock = Clock.systemUTC

def verifyLogin(request: Request[IO]): IO[Either[String,User]] = ??? // gotta figure out how to do the form
val logIn: Kleisli[IO, Request[IO], Response[IO]] = Kleisli({ request =>
  verifyLogin(request: Request[IO]).flatMap(_ match {
    case Left(error) =>
      Forbidden(error)
    case Right(user) => {
      val message = crypto.signToken(user.id.toString, clock.millis.toString)
      Ok("Logged in!").map(_.addCookie(Cookie("authcookie", message)))
    }
  })
})
```

Now that the cookie is set, we can retrieve it again in the `authUser`.

```scala mdoc
def retrieveUser: Kleisli[IO, Long, User] = Kleisli(id => IO(???))
val authUser3: Kleisli[IO, Request[IO], Either[String,User]] = Kleisli({ request =>
  val message = for {
    header <- headers.Cookie.from(request.headers).toRight("Cookie parsing error")
    cookie <- header.values.toList.find(_.name == "authcookie").toRight("Couldn't find the authcookie")
    token <- crypto.validateSignedToken(cookie.content).toRight("Cookie invalid")
    message <- Either.catchOnly[NumberFormatException](token.toLong).leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser.run)
})
```

### Authorization Header

There is no inherent way to set the Authorization header, send the token in any
way that your [SPA] understands. Retrieve the header value in the `authUser`
function.

```scala mdoc
import org.http4s.util.string._
import org.http4s.headers.Authorization

val authUser4: Kleisli[IO, Request[IO], Either[String,User]] = Kleisli({ request =>
  val message = for {
    header <- request.headers.get(Authorization).toRight("Couldn't find an Authorization header")
    token <- crypto.validateSignedToken(header.value).toRight("Cookie invalid")
    message <- Either.catchOnly[NumberFormatException](token.toLong).leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser.run)
})
```

### Using tsec-http4s for Authentication and Authorization
The [TSec] project provides an authentication and authorization module
 for the http4s project 0.18-M4+. Docs specific to http4s are located [Here](https://jmcardon.github.io/tsec/docs/http4s-auth.html).

[service]: ../service
[SPA]: https://en.wikipedia.org/wiki/Single-page_application
[ADT]: https://typelevel.org/blog/2014/11/10/why_is_adt_pattern_matching_allowed.html
[TSec]: https://jmcardon.github.io/tsec/
