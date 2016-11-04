---
layout: default
title: Authentication
---
## Authentication

A [service] is a `Kleisli[Task, Request, Response]`, the composable version of
`Request => Task[Response]`. http4s provides an alias called `Service[Request,
Response]`. A service with authentication also requires some kind of `User`
object which identifies which user did the request. To store the `User` object
along with the `Request`, there's `AuthedRequest[User]`, which is equivalent to
`(User, Request)`. So the service has the signature `AuthedRequest[User] =>
Task[Response]`, or `AuthedService[User]`. So we'll need a `Request => User`
function, or more likely, a `Request => Task[User]`, because the `User` will
come from a database. We can convert that into an `AuthMiddleware` and apply it.
Or in code:

```tut:book
import scalaz._, Scalaz._, scalaz.concurrent.Task
import org.http4s._
import org.http4s.dsl._
import org.http4s.server._

case class User(id: Long, name: String)

val authUser: Service[Request, User] = Kleisli(_ => Task.delay(???))
val middleware = AuthMiddleware(authUser)
val authedService: AuthedService[User] =
  AuthedService {
    case GET -> Root / "welcome" as user => Ok(s"Welcome, ${user.name}")
  }
val service: HttpService = middleware(authedService)
```

## Returning an Error Response

Usually, it should also be possible to send back a 401 in case there was no
valid login. The 401 response can be adjusted as needed, some applications use a
redirect to a login page, or a popup requesting login data. With the upcoming of
[SPA], the correct http status codes are relevant again.

### With Kleisli

To allow for failure, the `authUser` function has to be adjusted to a `Request
=> Task[String \/ User]`. So we'll need to handle that possibility. For advanced
error handling, we recommend an error [ADT] instead of a `String`.

```tut:book
val authUser: Kleisli[Task, Request, String \/ User] = Kleisli(_ => Task.delay(???))

val onFailure: AuthedService[String] = Kleisli(req => Forbidden(req.authInfo))
val middleware = AuthMiddleware(authUser, onFailure)

val service: HttpService = middleware(authedService)
```


## Implementing authUser

There's a few different ways to send authorization information with a HTTP
request, the two most common are cookie for regular browser usage or the
`Authorization` header for [SPA].

### Cookies

We'll use a small library for the signing/validation of the cookies, which
basically contains the code used by the Play framework for this specific task.

```scala
libraryDependencies += "org.reactormonk" %% "cryptobits" % "1.1"
```

First, we'll need to set the cookie. For the crypto instance, we'll need to
provide a private key. You usually want to set a static secret so people don't
lose their session on server restarts, and a static secret also allows you to
use multiple application instances.

The message is simply the user id.

```tut:book
import org.reactormonk.{CryptoBits, PrivateKey}
import java.time._

val key = PrivateKey(scala.io.Codec.toUTF8(scala.util.Random.alphanumeric.take(20).mkString("")))
val crypto = CryptoBits(key)
val clock = Clock.systemUTC

def verifyLogin(request: Request): Task[String \/ User] = ??? // gotta figure out how to do the form
val logIn: Service[Request, Response] = Kleisli({ request =>
  verifyLogin(request: Request).flatMap(_ match {
    case -\/(error) =>
      Forbidden(error)
    case \/-(user) => {
      val message = crypto.signToken(user.id.toString, clock.millis.toString)
      Ok("Logged in!").addCookie(Cookie("authcookie", message))
    }
  })
})
```

Now that the cookie is set, we can retrieve it again in the `authUser`.

```tut:book
def retrieveUser: Service[Long, User] = Kleisli(id => Task.delay(???))
val authUser: Service[Request, String \/ User] = Kleisli({ request =>
  val message = for {
    header <- headers.Cookie.from(request.headers).toRightDisjunction("Cookie parsing error")
    cookie <- header.values.list.find(_.name == "authcookie").toRightDisjunction("Couldn't find the authcookie")
    token <- crypto.validateSignedToken(cookie.content).toRightDisjunction("Cookie invalid")
    message <- \/.fromTryCatchNonFatal(token.toLong).leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser)
})
```

### Authorization Header

There is no inherent way to set the Authorization header, send the token in any
way that your [SPA] understands. Retrieve the header value in the `authUser`
function.

```tut:book
import org.http4s.util.string._
import org.http4s.headers.Authorization

val authUser: Service[Request, String \/ User] = Kleisli({ request =>
  val message = for {
    header <- request.headers.get(Authorization).toRightDisjunction("Couldn't find an Authorization header")
    token <- crypto.validateSignedToken(header.value).toRightDisjunction("Cookie invalid")
    message <- \/.fromTryCatchNonFatal(token.toLong).leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser)
})
```

[service]: service.html
[SPA]: https://en.wikipedia.org/wiki/Single-page_application
[ADT]: http://typelevel.org/blog/2014/11/10/why_is_adt_pattern_matching_allowed.html
