# Error Handling

A `MessageFailure` indicates an error handling an HTTP message.  These
include:

* `ParsingFailure`: indicative of a malformed HTTP message in the
  request line or headers.
* `MalformedMessageBodyFailure`: indicative of a message that has a
  syntactic error in its body.  For example, trying to decode `{
  "broken:"` as JSON will result in a `MalforedMessageBodyFailure`.
* `InvalidMessageBodyFailure`: indicative of a message that is
  syntactically correct, but semantically incorrect.  A well-formed
  JSON request that is missing expected fields may generate this
  failure.
* `MediaTypeMissing`: indicates that the message had no media type,
  and the server wasn't willing to infer it.
* `MediaTypeMismatch`: indicates that the server received a media
  type that it wasn't prepared to handle.

# For Beginners

When you start from a "clean slate" with http4s, one of the things you're likely to notice, is that http4s is swallowing your exceptions - let's see if we can prove it. Assuming you've gotten the hello world example started, let's introduce another route, which is going to error out.

```scala mdoc:silent
import cats.effect._
import cats.syntax.all._
import cats.effect.unsafe.IORuntime
import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling
import cats.data.OptionT
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
```

```scala mdoc:silent
import com.comcast.ip4s._

val errorRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "error" =>
        throw new Exception("Hey don't swallow me")
}

val server = EmberServerBuilder
  .default[IO]
  .withPort{port"8081"}
  .withHost(host"localhost")
  .withHttpApp(errorRoute.orNotFound)
  .build
```
Once you've started this server, hit up the "/error" route in browser. What you'll find, is that it returns a `500` response code... and no messages, and the console that hosts the http4s dev server is also showing you ... nothing.

Now, in general swallowing exceptions in software engineering is widely considered bad practise - now we have no idea our program is going wrong! Presumably in the case of http4s, having your public facing webserver "secure by default" trumps that consideration.

http4s provides an answer to this seeming paradox, in the form of [middleware](middleware.md). As this section is written by a beginner, we're going to assume you want to know your code is throwing exceptions and take the beginners path of least resistance to surfacing them.

We're going to make the path to instantiating the server look like this, instead.

```scala mdoc:silent
import org.http4s.server.middleware.ErrorAction
import org.http4s.server.middleware.ErrorHandling

def errorHandler(t: Throwable, msg: => String) : OptionT[IO, Unit] =
  OptionT.liftF(
      IO.println(msg) >>
      IO.println(t) >>
      IO(t.printStackTrace())
  )


val withErrorLogging = ErrorHandling.Recover.total(
  ErrorAction.log(
    errorRoute,
    messageFailureLogAction = errorHandler,
    serviceErrorLogAction = errorHandler
  )
)

val thisServerPrintsErrors = EmberServerBuilder
  .default[IO]
  .withPort(port"8081")
  .withHost(host"localhost")
  .withHttpApp(withErrorLogging.orNotFound)
  .build

```
And now, you'll get told which endpoint is failing, and get the stacktrace printed to the console. Leveling up your error handling experience through fancy logging frameworks, tracing et al is left as an excercise for the (no longer beginner) reader...

## Logging

If a `MessageFailure` is not handled by your HTTP service, it reaches
the backend in the form of a failed task, where it is transformed into
an HTTP response.  To guard against XSS attacks, care is taken in each
of these renderings to not reflect information back from the request.
Diagnostic information about syntax errors or missing fields,
including a full stack trace, is logged to the
`org.http4s.server.message-failures` category at `DEBUG` level.

## Customizing Error Handling

TODO
