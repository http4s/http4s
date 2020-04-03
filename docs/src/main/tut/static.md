---
menu: main
weight: 140
title: Static Files
---

Http4s can serve static files, subject to a configuration policy. There are three
locations that Http4s can serve static content from: the filesystem, resources
using the classloader, and WebJars.

All of these solutions are most likely slower than the equivalent in nginx or a
similar static file hoster, but they're often fast enough.

## Getting Started

To use fileService, the only configuration required is the relative path to the directory to serve.
The service will automatically serve index.html if the request path is not a file. This service will also
remove dot segments, to prevent attackers from reading files not contained in the directory
being served.

```tut:book
import cats.effect._
import cats.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Server
import org.http4s.server.staticcontent._
import org.http4s.syntax.kleisli._

object SimpleHttpServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    app.use(_ => IO.never).as(ExitCode.Success)

  val app: Resource[IO, Server] =
    for {
      blocker <- Blocker[IO]
      server <- BlazeServerBuilder[IO]
        .bindHttp(8080)
        .withHttpApp(fileService[IO](FileService.Config(".", blocker)).orNotFound)
        .resource
    } yield server
}
```

Static content services can be composed into a larger application by using a `Router`:
```tut:book:nofail
val httpApp: HttpApp[IO] =
    Router(
      "api"    -> anotherService,
      "assets" -> fileService(FileService.Config("./assets", blocker))
    ).orNotFound
```

## ETags

Usually, if you fetch a file via HTTP, it ships with an ETag. An ETag specifies
a file version. So the next time the browser requests that information, it sends
the ETag along, and gets a 304 Not Modified back, so you don't have to send the
data over the wire again.

### Execution Context

Static file support uses a blocking API, so we'll need a blocking execution
context. For this reason, the helpers in `org.http4s.server.staticcontent._` takes
an argument of type `cats.effect.Blocker`.
You can create a `Resource[F, Blocker]` by calling `Blocker[F]`, which will handle
creating and disposing of an underlying thread pool. You can also create your
own by lifting an execution context or an executor service.

For now, we will lift an executor service, since using `Resource` in a `tut` 
example is not feasible.

```tut:silent
import java.util.concurrent._

val blockingPool = Executors.newFixedThreadPool(4)
val blocker = Blocker.liftExecutorService(blockingPool)
```

It also needs a main thread pool to shift back to.  This is provided when
we're in IOApp, but you'll need one if you're following along in a REPL:

```tut:silent
import scala.concurrent.ExecutionContext

implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
```

In a production application, `ContextShift[IO]` will be supplied by `IOApp`
and the blocker would be created at app startup, using the `Resource` approach.

```tut:silent
val routes = fileService[IO](FileService.Config(".", blocker))
```

For custom behaviour, `StaticFile.fromFile` can also be used directly in a route, to respond with a file:
```tut:silent
import org.http4s._
import org.http4s.dsl.io._
import java.io.File

val routes = HttpRoutes.of[IO] {
  case request @ GET -> Root / "index.html" =>
    StaticFile.fromFile(new File("relative/path/to/index.html"), blocker, Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
}
```

## Serving from jars

For simple file serving, it's possible to package resources with the jar and
deliver them from there. For example, for all resources in the classpath under `assets`:

```tut:book
val routes = resourceService[IO](ResourceService.Config("/assets", blocker))
```

For custom behaviour, `StaticFile.fromResource` can be used. In this example,
only files matching a list of extensions are served. Append to the `List` as needed.

```tut:book
def static(file: String, blocker: Blocker, request: Request[IO]) =
  StaticFile.fromResource("/" + file, blocker, Some(request)).getOrElseF(NotFound())

val routes = HttpRoutes.of[IO] {
  case request @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
    static(path, blocker, request)
}
```

## Webjars

A special service exists to load files from [WebJars](http://www.webjars.org). Add your WebJar to the
class path, as you usually would:

```tut:book:nofail
libraryDependencies ++= Seq(
  "org.webjars" % "jquery" % "3.1.1-1"
)
```

Then, mount the `WebjarService` like any other service:

```tut:silent
import org.http4s.server.staticcontent.webjarService
import org.http4s.server.staticcontent.WebjarService.{WebjarAsset, Config}
```

```tut:book
// only allow js assets
def isJsAsset(asset: WebjarAsset): Boolean =
  asset.asset.endsWith(".js")

val webjars: HttpRoutes[IO] = webjarService(
  Config(
    filter = isJsAsset,
    blocker = blocker
  )
)
```

```tut:silent
blockingPool.shutdown()
```

Assuming that the service is mounted as root on port `8080`, and you included the webjar `swagger-ui-3.20.9.jar` on your classpath, you would reach the assets with the path: `http://localhost:8080/swagger-ui/3.20.9/index.html`

[StaticFile]: ../api/org/http4s/StaticFile$
