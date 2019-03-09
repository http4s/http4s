---
menu: main
weight: 140
title: Static Files
---

## On static data and HTTP
Usually, if you fetch a file via HTTP, it ships with an ETag. An ETag specifies
a file version. So the next time the browser requests that information, it sends
the ETag along, and gets a 304 Not Modified back, so you don't have to send the
data over the wire again.

All of these solutions are most likely slower than the equivalent in nginx or a
similar static file hoster, but they're often fast enough.

## Serving static files
Http4s provides a few helpers to handle ETags for you, they're located in [StaticFile].

```tut:silent
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import java.io.File
```

### Prerequisites

Static file support uses a blocking API, so we'll need a blocking execution
context:

```tut:silent
import java.util.concurrent._
import scala.concurrent.ExecutionContext

val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))
```

It also needs a main thread pool to shift back to.  This is provided when
we're in IOApp, but you'll need one if you're following along in a REPL:

```tut:silent
implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
```

```tut:silent
val routes = HttpRoutes.of[IO] {
  case request @ GET -> Root / "index.html" =>
    StaticFile.fromFile(new File("relative/path/to/index.html"), blockingEc, Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
}
```

## Serving from jars
For simple file serving, it's possible to package resources with the jar and
deliver them from there. Append to the `List` as needed.

```tut:book
def static(file: String, blockingEc: ExecutionContext, request: Request[IO]) =
  StaticFile.fromResource("/" + file, blockingEc, Some(request)).getOrElseF(NotFound())

val routes = HttpRoutes.of[IO] {
  case request @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
    static(path, blockingEc, request)
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
    blockingExecutionContext = blockingEc
  )
)
```

```tut:silent
blockingEc.shutdown()
```

Assuming that the service is mounted as root on port `8080`, and you included the webjar `swagger-ui-3.20.9.jar` on your classpath, you would reach the assets with the path: `http://localhost:8080/swagger-ui/3.20.9/index.html`

[StaticFile]: ../api/org/http4s/StaticFile$

