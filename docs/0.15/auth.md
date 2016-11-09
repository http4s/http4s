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

```scala
import scalaz._, Scalaz._, scalaz.concurrent.Task
// import scalaz._
// import Scalaz._
// import scalaz.concurrent.Task

import org.http4s._
// import org.http4s._

import org.http4s.dsl._
// import org.http4s.dsl._

import org.http4s.server._
// import org.http4s.server._

case class User(id: Long, name: String)
// defined class User

val authUser: Service[Request, User] = Kleisli(_ => Task.delay(???))
// authUser: org.http4s.Service[org.http4s.Request,User] = Kleisli(<function1>)

val middleware = AuthMiddleware(authUser)
// middleware: org.http4s.server.AuthMiddleware[User] = <function1>

val authedService: AuthedService[User] =
  AuthedService {
    case GET -> Root / "welcome" as user => Ok(s"Welcome, ${user.name}")
  }
// authedService: org.http4s.AuthedService[User] = Kleisli(<function1>)

val service: HttpService = middleware(authedService)
// service: org.http4s.HttpService = Kleisli(<function1>)
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

```scala
val authUser: Kleisli[Task, Request, String \/ User] = Kleisli(_ => Task.delay(???))
// authUser: scalaz.Kleisli[scalaz.concurrent.Task,org.http4s.Request,scalaz.\/[String,User]] = Kleisli(<function1>)

val onFailure: AuthedService[String] = Kleisli(req => Forbidden(req.authInfo))
// onFailure: org.http4s.AuthedService[String] = Kleisli(<function1>)

val middleware = AuthMiddleware(authUser, onFailure)
// middleware: org.http4s.server.AuthMiddleware[User] = <function1>

val service: HttpService = middleware(authedService)
// service: org.http4s.HttpService = Kleisli(<function1>)
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

```scala
import org.reactormonk.{CryptoBits, PrivateKey}
// import org.reactormonk.{CryptoBits, PrivateKey}

import java.time._
// import java.time._

val key = PrivateKey(scala.io.Codec.toUTF8(scala.util.Random.alphanumeric.take(20).mkString("")))
// key: org.reactormonk.PrivateKey = PrivateKey([B@c499eb7)

val crypto = CryptoBits(key)
// crypto: org.reactormonk.CryptoBits = CryptoBits(PrivateKey([B@c499eb7))

val clock = Clock.systemUTC
// clock: java.time.Clock = SystemClock[Z]

def verifyLogin(request: Request): Task[String \/ User] = ??? // gotta figure out how to do the form
// verifyLogin: (request: org.http4s.Request)scalaz.concurrent.Task[scalaz.\/[String,User]]

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
// logIn: org.http4s.Service[org.http4s.Request,org.http4s.Response] = Kleisli(<function1>)
```

Now that the cookie is set, we can retrieve it again in the `authUser`.

```scala
def retrieveUser: Service[Long, User] = Kleisli(id => Task.delay(???))
// retrieveUser: org.http4s.Service[Long,User]

val authUser: Service[Request, String \/ User] = Kleisli({ request =>
  val message = for {
    header <- headers.Cookie.from(request.headers).toRightDisjunction("Cookie parsing error")
    cookie <- header.values.list.find(_.name == "authcookie").toRightDisjunction("Couldn't find the authcookie")
    token <- crypto.validateSignedToken(cookie.content).toRightDisjunction("Cookie invalid")
    message <- \/.fromTryCatchNonFatal(token.toLong).leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser)
})
// authUser: org.http4s.Service[org.http4s.Request,scalaz.\/[String,User]] = Kleisli(<function1>)
```

### Authorization Header

There is no inherent way to set the Authorization header, send the token in any
way that your [SPA] understands. Retrieve the header value in the `authUser`
function.

```scala
import org.http4s.util.string._
// import org.http4s.util.string._

import org.http4s.headers.Authorization
// import org.http4s.headers.Authorization

val authUser: Service[Request, String \/ User] = Kleisli({ request =>
  val message = for {
    header <- request.headers.get(Authorization).toRightDisjunction("Couldn't find an Authorization header")
    token <- crypto.validateSignedToken(header.value).toRightDisjunction("Cookie invalid")
    message <- \/.fromTryCatchNonFatal(token.toLong).leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser)
})
// authUser: org.http4s.Service[org.http4s.Request,scalaz.\/[String,User]] = Kleisli(<function1>)
```

[service]: service.html
[SPA]: https://en.wikipedia.org/wiki/Single-page_application
[ADT]: http://typelevel.org/blog/2014/11/10/why_is_adt_pattern_matching_allowed.html
