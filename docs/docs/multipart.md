# Multipart and Form Handling

Multipart is a content type for messages (requests or responses) composed of multiple parts.
Each part is itself similar to a message in that it has its own body and headers. A common use
for multipart requests is for user-submitted forms, especially ones that include files.
Browsers also support (and default to) another encoding for forms, `application/x-www-form-urlencoded`.
This encoding is simpler, but is not often used for binary data.

## UrlForm

To handle `application/x-www-form-urlencoded` messages, http4s provides `UrlForm` and respective `EntityEncoder` and 
`EntityDecoder`. The following example shows the client sending a form request and a server parsing it:

```scala mdoc:silent
import org.http4s.client.Client
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.multipart._
import org.http4s.implicits._

import cats.effect.unsafe.implicits.global

val routes = HttpRoutes
  .of[IO] { case request @ POST -> Root / "url-form" =>
    request.as[UrlForm].flatMap { form =>
      val name = form.values
        .collectFirst { case ("name", values) => values }
        .flatMap(_.headOption)
        .getOrElse("")
      Ok(s"Hello, $name")
    }
  }

val client = Client.fromHttpApp(routes.orNotFound)
val request = Request[IO](
  method = POST,
  uri = uri"http://example/url-form",
).withEntity(UrlForm("name" -> "Duncan", "version" -> "4"))

```
```scala mdoc
client.expect[String](request)
  .unsafeRunSync()
```

You can try this self-contained example using `scala-cli` and pointing your browser to http://localhost:8089/.
It includes a page with a form and the endpoint receiving the submission.
```scala mdoc:compile-only

//> using scala "2.13.12"
//> using dep "org.http4s::http4s-ember-client:@VERSION@"
//> using dep "org.http4s::http4s-ember-server:@VERSION@"
//> using dep "org.http4s::http4s-dsl:@VERSION@"

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers._

object Main extends IOApp.Simple {
  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "url-form" =>
      Ok(
        """
          |<form method="post">
          |  <label for="name">Name</label>
          |  <input id="name" name="name" />
          |  <button>Submit</button>
          |</form>
          |""".stripMargin
      ).map(_.withContentType(`Content-Type`(MediaType.text.html)))

    case req @ POST -> Root / "url-form" =>
      req.as[UrlForm].flatMap { form =>
        Ok(
          form.values
            .map { case (k, v) => s"$k: ${v.mkString_(",")}" }
            .toList
            .mkString_("\n")
        )
      }
  }

  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"8089")
      .withHttpApp(routes.orNotFound)
      .build
      .useForever
}
```

## Multipart form

http4s also supports multipart forms, although their usage is a bit more involved. A multipart body is represented
with a `Multipart[_]` value. There's an `EntityDecoder` for `Multipart[_]`, so parsing a request body works as expected:  
```scala mdoc:silent
HttpRoutes.of[IO] {
  case request @ POST -> Root / "multipart-form" =>
    request.as[Multipart[IO]].flatMap(multipart => ???)
}
```
However, this approach buffers the contents in memory and so it's unsuitable if large requests are 
expected. The size of the body can be controlled using the [EntityLimiter] middleware, and it's advisable
to use it generally, even if no uploads are expected.

An alternative that handles large request bodies better is `EntityDecoder.mixedMultipartResource` which will stream
the body into files on disk if it reaches a specified size threshold. This method returns a `Resource` wrapping an `EntityDecoder`,
the resource is meant to be allocated in the scope of a single request as it cleans up any temporary files that
were created while decoding. Usage is as follows:

```scala mdoc:silent
HttpRoutes.of[IO] {
  case request @ POST -> Root / "multipart-form" =>
    EntityDecoder.mixedMultipartResource[IO]().use(decoder =>
      request.decodeWith(decoder, strict = true)(multipart => ???)
    )
}
```

Creating a multipart request also differs slightly from other content types. Each part in a multipart request body is
surrounded by a boundary, which is a bit of text used by the server to distinguish between the different parts. This
text is also sent in the header of the request. Currently, the `EntityEncoder` can't define headers by inspecting the body,
and as such we have to define the boundary header in the request explicitly. Additionally, the boundary is randomly-generated,
which is an effect. We can call methods on the `Multiparts` companion object to get a `Multiparts[_]` instance, which is 
a builder of multipart requests. This instance can be shared. This is an example of the creation of a multipart request:

```scala mdoc:silent
Multiparts.forSync[IO].flatMap(multiparts =>
  multiparts.multipart(
    Vector( // a multipart request with two parts
      Part.fileData[IO]( // there are overloads for fileData that read directly from a file
        name = "picture",
        filename = "sunset.jpg",
        entityBody = fs2.Stream.range[IO, Int](0, 100).map(_.toByte),
        headers = `Content-Type`(MediaType.image.jpeg)
      ),
      Part.formData[IO](name = "description", value = "A sunset")
    )
  )
)
  .map(multipartRequest =>
    Request[IO](
      method = Method.POST,
      uri = uri"http://example.com/",
      headers = multipartRequest.headers // set the headers related to this multipart request
    ).withEntity(multipartRequest)
  )
```

Here's a full example with a client and a server:
```scala mdoc:silent:nest
import org.http4s.client.Client
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.multipart._
import org.http4s.implicits._

import cats.effect.unsafe.implicits.global

val routes = HttpRoutes
  .of[IO] { case request @ POST -> Root / "multipart-form" =>
    EntityDecoder.mixedMultipartResource[IO]().use(decoder =>
      request.decodeWith(decoder, strict = true) { multipart =>
        val picture = multipart.parts.find(_.name.contains("picture"))
        val pictureSize = picture.traverse(_.body.compile.count).map(_.getOrElse(0L))

        val description = multipart.parts.find(_.name.contains("description"))
        val descriptionText = description.traverse(_.bodyText.compile.string).map(_.getOrElse(""))

        (pictureSize, descriptionText)
          .flatMapN((size, description) => 
            Ok(s"This is a $size byte file, with the description '$description'")
          )
      }
    )
  }

val client = Client.fromHttpApp(routes.orNotFound)
val request = Multiparts.forSync[IO].flatMap(multiparts =>
    multiparts.multipart(
      Vector(
        Part.fileData[IO](
          name = "picture",
          filename = "sunset.jpg",
          entityBody = fs2.Stream.range[IO, Int](0, 100).map(_.toByte),
          headers = `Content-Type`(MediaType.image.jpeg)
        ),
        Part.formData[IO](name = "description", value = "A sunset")
      )
    )
  )
  .map(multipartRequest =>
    Request[IO](
      method = Method.POST,
      uri = uri"http://example.com/multipart-form",
      headers = multipartRequest.headers
    )
      .withEntity(multipartRequest)
  )

```
```scala mdoc
request.flatMap(client.expect[String](_))
  .unsafeRunSync()
```

Like [UrlForm], browsers can also submit forms in a multipart request, to use this encoding the `enctype` attribute is
used in the `form` element, like this: `<form method="post" enctype="multipart/form-data">`. 

## Streaming uploads

The usage of multipart is somewhat convoluted, in part because one expects a fixed-size sequence of parts when processing
a request (notice that `parts` is a `Vector`, not a `Stream`), this means that http4s has to get to
the end of the request so that it knows all the parts. But this isn't the only way to upload files (although it is the 
only way to do it in pure HTML). A simpler form of upload could just stream the binary data in the request, like so:

```scala mdoc:silent:nest
import org.http4s.client.Client
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.typelevel.ci._

import cats.effect.unsafe.implicits.global

val routes = HttpRoutes
  .of[IO] { case request @ POST -> Root / "upload" =>
    val fileSize = request.body.compile.count
    fileSize
      .flatMap(size => Ok(s"This is a $size byte file"))
  }

val client = Client.fromHttpApp(routes.orNotFound)
val request = Request[IO](
  method = Method.POST,
  uri = uri"http://example.com/upload",
).withEntity(fs2.Stream.range[IO, Int](0, 100).map(_.toByte))
```
```scala mdoc
client.expect[String](request)
  .unsafeRunSync()
```

This alternative allows the server to work in a fully streaming fashion, although it's obviously missing the `description`
from our previous example. The other parts could be put into the query string or headers (taking into account their encoding and size limitations)
or in subsequent requests. This type of request with a binary payload can also be created in Javascript, and thus can
be initiated from the browser.

[EntityLimiter]: server-middleware.md#entitylimiter 
[UrlForm]: #urlform
