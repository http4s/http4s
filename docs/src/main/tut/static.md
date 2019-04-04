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
import org.http4s.server.staticcontent._
import org.http4s.syntax.kleisli._

object SimpleHttpServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080)
      .withHttpApp(fileService[IO](FileService.Config(".")).orNotFound)
      .serve
      .compile.drain.as(ExitCode.Success)
}
```

Static content services can be composed into a larger application by using a `Router`:
```tut:book:nofail
val httpApp: HttpApp[IO] =
    Router(
      "api"    -> anotherService
      "assets" -> fileService(FileService.Config("./assets))
    ).orNotFound
```

## ETags

Usually, if you fetch a file via HTTP, it ships with an ETag. An ETag specifies
a file version. So the next time the browser requests that information, it sends
the ETag along, and gets a 304 Not Modified back, so you don't have to send the
data over the wire again.

### Execution Context

Static file support uses a blocking API, so we'll need a blocking execution
context. The helpers in `org.http4s.server.staticcontent._` will use the global execution context, but
for best results this should overriden according to the desired characteristics of your server.  

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
val routes = fileService[IO](FileService.Config(".", executionContext = blockingEc))
```

For custom behaviour, `StaticFile.fromFile` can also be used directly in a route, to respond with a file:
```tut:silent
import org.http4s._
import org.http4s.dsl.io._
import java.io.File

val routes = HttpRoutes.of[IO] {
  case request @ GET -> Root / "index.html" =>
    StaticFile.fromFile(new File("relative/path/to/index.html"), blockingEc, Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
}
```

## Serving from jars

For simple file serving, it's possible to package resources with the jar and
deliver them from there. For example, for all resources in the classpath under `assets`:

```tut:book
val routes = resourceService[IO](ResourceService.Config("/assets", ExecutionContext.global))
```

For custom behaviour, `StaticFile.fromResource` can be used. In this example, 
only files matching a list of extensions are served. Append to the `List` as needed.

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

