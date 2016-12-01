---
layout: docs
title: URI handling
---

## Literals

http4s is a bit more strict with handling URIs than e.g. the [play http client].
Instead of passing plain `String`s, http4s operates on URIs. You can construct
literal URI with

```tut:book
import org.http4s._

val uri = Uri.uri("http://http4s.org")
```

## Building URIs

Naturally, that's not enough if you want dynamic URIs. There's a few different
ways to build URIs, you can either use a predefined URI and call methods on it,
or you could use the URLTemplates.

### URI

Use the methods on the [uri class].

```tut:book
val docs = uri.withPath("/docs/0.15/")
```

### URI Template

```tut:book
import org.http4s.util.CaseInsensitiveString.ToCaseInsensitiveStringSyntax
import org.http4s.UriTemplate._

val template = UriTemplate(
  authority = Some(Uri.Authority(host = Uri.RegName("http4s.org"))),
  scheme = Some("http".ci),
  path = List(PathElm("docs"), PathElm("0.15"))
)

template.toUriIfPossible
```

## Receiving URIs
URIs come in as strings from external routes or as data. Http4s provides
encoders/decoders for `Uri` in the connector packages.

For example one for [knobs]:

```
implicit val configuredUri = Configured[String].flatMap(s => Configured(_ => Uri.fromString(s).toOption))
```

[play http client]: https://www.playframework.com/documentation/2.5.x/api/scala/index.html#play.api.libs.ws.WS$
[uri class]: http://http4s.org/api/0.15/#org.http4s.Uri
[knobs]: https://github.com/Verizon/knobs
