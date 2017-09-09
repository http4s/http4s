---
menu: main
weight: 300
title: Entity handling
---

## Why Entity*

Http4s handles HTTP requests and responses in a streaming fashion. Your service
will receive a request after the header has been parsed (ok, not 100%
streaming), but before the body has been fully received. The same applies to the
http client usage, where you can start a connection before the body is fully
materialized. You don't have to load the full body into memory to submit the
request either. Taking a look at `Request` and `Response`, both have a body of
type `EntityBody`, which is simply an alias to `Process[Task, ByteVector]`. To
understand `Process`, take a look at the [streams-tutorial].

The `EntityDecoder` and `EntityEncoder` help with the streaming nature of the
data in a http body, and they also have additional logic to deal with media
types. Not all decoders are streaming, depending on the implementation.

## Construction and Media Types
`Entity*`s also encode which media types they correspond to. The
`EntityDecoder`s for json expect `application/json`. To implement this
functionality, the constructor `EntityDecoder.decodeBy` uses `MediaRange`s. You
can pass multiple as needed. You can also append functionality to an existing
one via `EntityDecoder[T].map` - however, you can't change the media
type in that case.

 When you encode a body with the `EntityEncoder` for json, it appends the
`Content-Type: application/json` header. You can construct new encoders via
`EntityEncoder.encodeBy` or reuse an already existing one via
`EntityEncoder[T].contramap` and `withContentType`.

See the [MediaRange] companion object for ranges, and [MediaType] for specific
types. Because of the implicit conversions, you can also use `(String, String)`
for a `MediaType`.

By default, decoders content types are ignored since it could lead to unexpected
runtime errors.

## Chaining Decoders

Decoders' content types are used when chaining decoders with `orElse` in order to
determine which of the chained decoders are to be used.

```tut
import org.http4s._
import org.http4s.dsl._
import scalaz._, Scalaz._

sealed trait Resp
case class Audio(body: String) extends Resp
case class Video(body: String) extends Resp

val response = Ok().withBody("").withContentType(Some(MediaType.`audio/ogg`))
val audioDec = EntityDecoder.decodeBy(MediaType.`audio/ogg`) { msg =>
  EitherT {
    msg.as[String].map(s => Audio(s).right[DecodeFailure])
  }
}
val videoDec = EntityDecoder.decodeBy(MediaType.`video/ogg`) { msg =>
  EitherT {
    msg.as[String].map(s => Video(s).right[DecodeFailure])
  }
}
val bothDec = audioDec.widen[Resp] orElse videoDec.widen[Resp]
println(response.as(bothDec).run)
```

## Presupplied Encoders/Decoders
The `EntityEncoder`/`EntityDecoder`s shipped with http4s.

### Raw Data Types
These are already in implicit scope by default, e.g. `String`, `File`,
`Future[_]`, and `InputStream`. Consult [EntityEncoder] and [EntityDecoder] for
a full list.

### JSON
With `jsonOf` for the `EntityDecoder`, and `jsonEncoderOf` for the `EntityEncoder`:

- argonaut: `"org.http4s" %% "http4s-argonaut" % Http4sVersion`
- circe: `"org.http4s" %% "http4s-circe" % Http4sVersion`
- json4s-native: `"org.http4s" %% "http4s-json4s-native" % Http4sVersion`
- json4s-jackson: `"org.http4s" %% "http4s-json4s-jackson" % Http4sVersion`

### XML
For scala-xml (xml literals), import `org.http4s.scalaxml`. No direct naming
required here, because there is no Decoder instance for `String` that would
cause conflicts with the builtin Decoders.

- scala-xml: `"org.http4s" %% "http4s-scala-xml" % Http4sVersion`

### Twirl
If you're working with [twirl] templates, there's a bridge for that too:

- scala-twirl: `"org.http4s" %% "http4s-twirl" % Http4sVersion`

[streams-tutorial]: https://gist.github.com/djspiewak/d93a9c4983f63721c41c
[EntityEncoder]: ../api/org/http4s/EntityEncoder$
[EntityDecoder]: ../api/org/http4s/EntityDecoder$
[MediaType]: ../api/org/http4s/MediaType$
[MediaRange]: ../api/org/http4s/MediaRange$
[twirl]: https://github.com/playframework/twirl
