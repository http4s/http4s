---
layout: default
title: Dealing with Entity Bodies
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

## Working with Media Types
`Entity*`s also encode which media types they correspond to. The
`EntityDecoders` for json expect `application/json`, and when you encode a body
with the `EntityEncoder` for json, it appends the `Content-Type:
application/json` header. To implement this functionality, the constructor
`EntityDecoder.decodeBy` uses `MediaRange`s, you can pass multiple as needed.
See the [MediaRange] companion object for ranges, and [MediaType] for specific
types. Because of the implicit conversions, you can also use `(String, String)`
for a `MediaType`.

### Strictness

By default, the decoders are strict, so you will get an error when the headers
tell you about a different data type. In some cases, an outside API might be
broken and give you text/text as `Content-Type`, but it's really json.

TODO: What do?

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

[streams-tutorial]: https://gist.github.com/djspiewak/d93a9c4983f63721c41c
[EntityEncoder]: http://http4s.org/api/0.15/#org.http4s.EntityEncoder$
[EntityDecoder]: http://http4s.org/api/0.15/#org.http4s.EntityDecoder$
[MediaType]: http://http4s.org/api/0.15/#org.http4s.MediaType$
[MediaRange]: http://http4s.org/api/0.15/#org.http4s.MediaRange$
