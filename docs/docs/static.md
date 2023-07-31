# Static Files

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

```scala mdoc
import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.staticcontent._

object SimpleHttpServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    app.use(_ => IO.never).as(ExitCode.Success)

  val app: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(fileService[IO](FileService.Config(".")).orNotFound)
      .build
}
```

Static content services can be composed into a larger application by using a `Router`:
```scala
val httpApp: HttpApp[IO] =
    Router(
      "api"    -> anotherService,
      "assets" -> fileService(FileService.Config("./assets"))
    ).orNotFound
```

## ETags

Usually, if you fetch a file via HTTP, it ships with an ETag. An ETag specifies
a file version. So the next time the browser requests that information, it sends
the ETag along, and gets a 304 Not Modified back, so you don't have to send the
data over the wire again.

## Inline in a Route

For custom behaviour, `StaticFile.fromPath` can also be used directly in a route, to respond with a file:

```scala mdoc:silent
import org.http4s._
import org.http4s.dsl.io._
import fs2.io.file.Path

val routes = HttpRoutes.of[IO] {
  case request @ GET -> Root / "index.html" =>
    StaticFile.fromPath(Path("relative/path/to/index.html"), Some(request))
      .getOrElseF(NotFound()) // In case the file doesn't exist
}
```

## Serving from JARs

For simple file serving, it's possible to package resources with the jar and
deliver them from there. For example, for all resources in the classpath under `assets`:

```scala mdoc:silent
val assetsRoutes = resourceServiceBuilder[IO]("/assets").toRoutes
```

For custom behaviour, `StaticFile.fromResource` can be used. In this example,
only files matching a list of extensions are served. Append to the `List` as needed.

```scala mdoc:silent
def static(file: String, request: Request[IO]) =
  StaticFile.fromResource("/" + file, Some(request)).getOrElseF(NotFound())

val fileTypes = List(".js", ".css", ".map", ".html", ".webm")

val fileRoutes = HttpRoutes.of[IO] {
  case request @ GET -> Root / path if fileTypes.exists(path.endsWith) =>
    static(path, request)
}
```

## Webjars

A special service exists to load files from [WebJars](http://www.webjars.org). Add your WebJar to the
class path, as you usually would:

```scala
libraryDependencies ++= Seq(
  "org.webjars" % "jquery" % "3.1.1-1"
)
```

Then, mount the `WebjarService` like any other service:

```scala mdoc:silent
import org.http4s.server.staticcontent.WebjarServiceBuilder.WebjarAsset
```

```scala mdoc:silent
// only allow js assets
def isJsAsset(asset: WebjarAsset): Boolean =
  asset.asset.endsWith(".js")

val webjars: HttpRoutes[IO] = webjarServiceBuilder[IO]
  .withWebjarAssetFilter(isJsAsset)
  .toRoutes
```

Assuming that the service is mounted as root on port `8080`, and you included the webjar `swagger-ui-3.20.9.jar` on your classpath, you would reach the assets with the path: `http://localhost:8080/swagger-ui/3.20.9/index.html`

[StaticFile]: @API_URL@/api/org/http4s/StaticFile$
[mdoc]: https://scalameta.org/mdoc/
