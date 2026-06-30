# Authentication

## Built in

For this section, remember that, like mentioned in the [service] section, a service is a
`Kleisli[OptionT[F, *], Request[F], Response[F]]`, the composable version of `Request[F] => OptionT[F, Response[F]]`.

Lets start by defining all the imports we will need in the examples below:

```scala mdoc:silent
import cats._, cats.effect._, cats.implicits._, cats.data._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server._
```

To add authentication to a service, we need some kind of `User` object which identifies the user
who sent the request. We represent that with `AuthedRequest[F, User]`, which allows you to reference
such object, and is the equivalent to `(User, Request[F])`. _http4s_ provides you with `AuthedRequest`,
but you have to provide your own _user_, or _authInfo_ representation. For our purposes here we will
use the following definition:

```scala mdoc:silent
case class User(id: Long, name: String)
```

With the request representation defined, we can move on to the `AuthedRoutes[User, F]`, an alias for
`AuthedRequest[F, User] => OptionT[F, Response[F]]`. Notice the similarity to a "normal" service, which
would be the equivalent to `Request[F] => OptionT[F, Response[F]]` - in other words, we are lifting the
`Request` into an `AuthedRequest`, and adding authentication information in the mix.

With that we can represent a service that requires authentication, but to actually construct it we need
to define how to extract the authentication information from the request. For that, we need a function
with the following signature: `Request[F] => OptionT[F, User]`. Here is an example of how to define it:

```scala mdoc:silent
val authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
  Kleisli(_ => OptionT.liftF(IO(???)))
```

It is worth noting that we are still wrapping the user fetch in `F` (`IO` in this case), because actually
discovering the user might require reading from a database or calling some other service - i.e. performing
IO operations.

Now we need a middleware that can bridge a "normal" service into an `AuthedRoutes`, which is quite easy to
get using our function defined above. We use `AuthMiddleware` for that:

```scala mdoc:silent
val middleware: AuthMiddleware[IO, User] =
  AuthMiddleware(authUser)
```

Finally, we can create our `AuthedRoutes`, and wrap it with our authentication middleware, getting the
final `HttpRoutes` to be exposed. Notice that we now have access to the user object in the service implementation:

```scala mdoc:silent
val authedRoutes: AuthedRoutes[User, IO] =
  AuthedRoutes.of {
    case GET -> Root / "welcome" as user => Ok(s"Welcome, ${user.name}")
  }

val service: HttpRoutes[IO] = middleware(authedRoutes)
```

## Composing Authenticated Routes

The default apply method of `AuthMiddleware` will consume all requests either unmatched, or
not authenticated by returning an empty response with status code 401 (Unauthorized). This mitigates
a kind of reconnaissance called "spidering", useful for white and black hat hackers to enumerate
your api for possible unprotected points.

```scala mdoc:silent
val spanishRoutes: AuthedRoutes[User, IO] =
    AuthedRoutes.of {
        case GET -> Root / "hola" as user => Ok(s"Hola, ${user.name}")
    }

val frenchRoutes: HttpRoutes[IO] =
    HttpRoutes.of {
        case GET -> Root / "bonjour" => Ok(s"Bonjour")
    }

val serviceSpanish: HttpRoutes[IO] =
  middleware(spanishRoutes) <+> frenchRoutes
```

Call to the french routes will always return 401 (Unauthorized) as they are caught by the spanish routes. To allow access to other routes you can:

* Use a Router with unique route prefixes

```scala mdoc:silent
val serviceRouter = {
  Router (
    "/spanish" -> middleware(spanishRoutes),
    "/french" -> frenchRoutes
  )
}
```

* Allow fallthrough, using `AuthMiddleware.withFallThrough`.

```scala mdoc:silent
val middlewareWithFallThrough: AuthMiddleware[IO, User] =
  AuthMiddleware.withFallThrough(authUser)
val serviceSF: HttpRoutes[IO] =
  middlewareWithFallThrough(spanishRoutes) <+> frenchRoutes
```

* Reorder the routes so that authed routes compose last

```scala mdoc:silent
val serviceFS: HttpRoutes[IO] =
  frenchRoutes <+> middlewareWithFallThrough(spanishRoutes)
```

Alternatively, to customize the behavior on not authenticated if you do not
wish to always return 401, use `AuthMiddleware.noSpider` and specify the `onAuthFailure` handler.

## Returning an Error Response

Usually, it should also be possible to send back a 401 in case there was no
valid login. The 401 response can be adjusted as needed, some applications use a
redirect to a login page, or a popup requesting login data. With the upcoming of
[SPA], the correct http status codes are relevant again.

### With Kleisli

To allow for failure, the `authUser` function has to be adjusted to a `Request[F]
=> F[Either[String,User]]`. So we'll need to handle that possibility. For advanced
error handling, we recommend an error [ADT] instead of a `String`.

```scala mdoc:silent
val authUserEither: Kleisli[IO, Request[IO], Either[String,User]] =
  Kleisli(_ => IO(???))

val onFailure: AuthedRoutes[String, IO] =
  Kleisli(req => OptionT.liftF(Forbidden(req.context)))

val authMiddleware = AuthMiddleware(authUserEither, onFailure)

val serviceKleisli: HttpRoutes[IO] = authMiddleware(authedRoutes)
```

## Implementing authUser

There's a few different ways to send authorization information with a HTTP
request, the two most common are cookie for regular browser usage or the
`Authorization` header for [SPA].

### Cookies

We'll use a small library for the signing/validation of the cookies, which
basically contains the code used by the Play framework for this specific task.

```scala
libraryDependencies += "org.reactormonk" %% "cryptobits" % "@CRYPTOBITS_VERSION@"
```

First, we'll need to set the cookie. For the crypto instance, we'll need to
provide a private key. You usually want to set a static secret so people don't
lose their session on server restarts, and a static secret also allows you to
use multiple application instances.

The message is simply the user id.

```scala mdoc:silent
import org.reactormonk.{CryptoBits, PrivateKey}
import scala.io.Codec
import scala.util.Random

val key = PrivateKey(Codec.toUTF8(Random.alphanumeric.take(20).mkString("")))
val crypto = CryptoBits(key)
val clock = java.time.Clock.systemUTC

// gotta figure out how to do the form
def verifyLogin(request: Request[IO]): IO[Either[String,User]] = ???

val logIn: Kleisli[IO, Request[IO], Response[IO]] = Kleisli({ request =>
  verifyLogin(request: Request[IO]).flatMap(_ match {
    case Left(error) =>
      Forbidden(error)
    case Right(user) => {
      val message = crypto.signToken(user.id.toString, clock.millis.toString)
      Ok("Logged in!").map(_.addCookie(ResponseCookie("authcookie", message)))
    }
  })
})
```

Now that the cookie is set, we can retrieve it again in the `authUser`.

```scala mdoc:silent
import org.http4s.headers.Cookie

def retrieveUser: Kleisli[IO, Long, User] = Kleisli(id => IO(???))
val authUserCookie: Kleisli[IO, Request[IO], Either[String,User]] = Kleisli({ request =>
  val message = for {
    header  <- request.headers.get[Cookie]
                 .toRight("Cookie parsing error")
    cookie  <- header.values.toList.find(_.name == "authcookie")
                 .toRight("Couldn't find the authcookie")
    token   <- crypto.validateSignedToken(cookie.content)
                 .toRight("Cookie invalid")
    message <- Either.catchOnly[NumberFormatException](token.toLong)
                 .leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser.run)
})
```

### Authorization Header

There is no inherent way to set the Authorization header, send the token in any
way that your [SPA] understands. Retrieve the header value in the `authUser`
function.

```scala mdoc:silent
import org.http4s.syntax.header._
import org.http4s.headers.Authorization

val authUserHeader: Kleisli[IO, Request[IO], Either[String,User]] = Kleisli({ request =>
  val message = for {
    header  <- request.headers.get[Authorization]
                 .toRight("Couldn't find an Authorization header")
    token   <- crypto.validateSignedToken(header.value)
                 .toRight("Invalid token")
    message <- Either.catchOnly[NumberFormatException](token.toLong)
                 .leftMap(_.toString)
  } yield message
  message.traverse(retrieveUser.run)
})
```

[service]: service.md
[SPA]: https://en.wikipedia.org/wiki/Single-page_application
[ADT]: https://typelevel.org/blog/2014/11/10/why_is_adt_pattern_matching_allowed.html
