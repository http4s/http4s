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

```tut:book
import org.http4s._
import org.http4s.dsl._
import java.io.File
import scalaz.concurrent.Task

val service = HttpService {
  case request @ GET -> Root / "index.html" =>
    StaticFile.fromFile(new File("relative/path/to/index.html"), Some(request))
      .map(Task.now) // This one is require to make the types match up
      .getOrElse(NotFound()) // In case the file doesn't exist
}
```

## Serving from jars
For simple file serving, it's possible to package resources with the jar and
deliver them from there. Append to the `List` as needed.

```tut:book
def static(file: String, request: Request) =
  StaticFile.fromResource("/" + file, Some(request)).map(Task.now).getOrElse(NotFound())

val service = HttpService {
  case request @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
    static(path, request)
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

```tut:book
import org.http4s.server.staticcontent.webjarService
import org.http4s.server.staticcontent.WebjarService.{WebjarAsset, Config}

// only allow js assets
def isJsAsset(asset: WebjarAsset): Boolean =
  asset.asset.endsWith(".js")

val webjars: HttpService = webjarService(
  Config(
    filter = isJsAsset
  )
)
```

[StaticFile]: ../api/org/http4s/StaticFile$

